/*
 *     FileSponge
 *     Copyright (C) 2021 Andrea Cavalli
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.warp.filesponge;

import static org.warp.filesponge.FileSponge.BLOCK_SIZE;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Resource;
import io.netty5.buffer.api.Send;
import it.cavallium.dbengine.database.BufSupplier;
import it.cavallium.dbengine.database.ColumnUtils;
import it.cavallium.dbengine.database.LLDatabaseConnection;
import it.cavallium.dbengine.database.LLDictionary;
import it.cavallium.dbengine.database.LLDictionaryResultType;
import it.cavallium.dbengine.database.LLKeyValueDatabase;
import it.cavallium.dbengine.database.LLUtils;
import it.cavallium.dbengine.database.UpdateMode;
import it.cavallium.dbengine.database.UpdateReturnMode;
import it.cavallium.dbengine.database.serialization.SerializationException;
import it.cavallium.dbengine.rpc.current.data.DatabaseOptions;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;
import org.warp.filesponge.DiskMetadata.DiskMetadataSerializer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class DiskCache implements URLsDiskHandler, URLsWriter {

	private final DiskMetadataSerializer diskMetadataSerializer;

	private final LLKeyValueDatabase db;
	private final LLDictionary fileContent;
	private final LLDictionary fileMetadata;
	private final Predicate<URL> shouldCache;

	public DiskCache(LLKeyValueDatabase db,
			LLDictionary fileContent,
			LLDictionary fileMetadata,
			Predicate<URL> shouldCache) {
		this.db = db;
		this.fileContent = fileContent;
		this.fileMetadata = fileMetadata;
		this.diskMetadataSerializer = new DiskMetadataSerializer();
		this.shouldCache = shouldCache;
	}

	public static Mono<DiskCache> open(LLDatabaseConnection databaseConnection,
			String dbName,
			DatabaseOptions databaseOptions,
			Predicate<URL> shouldCache) {
		return databaseConnection
				.getDatabase(dbName,
						List.of(ColumnUtils.dictionary("file-content"), ColumnUtils.dictionary("file-metadata")),
						databaseOptions
				)
				.flatMap(db -> Mono.zip(
						Mono.just(db).single(),
						db.getDictionary("file-content", UpdateMode.ALLOW).single(),
						db.getDictionary("file-metadata", UpdateMode.ALLOW).single()
				))
				.map(tuple -> new DiskCache(tuple.getT1(), tuple.getT2(), tuple.getT3(), shouldCache))
				.single();
	}

	@Override
	public Mono<Void> writeMetadata(URL url, Metadata metadata) {
		// Check if this cache should cache the url, otherwise do nothing
		if (!shouldCache.test(url)) return Mono.empty();

		Mono<Buffer> keyMono = Mono.fromCallable(() -> serializeUrl(url));
		return fileMetadata
				.update(keyMono,
						oldValue -> Objects.requireNonNullElseGet(oldValue,
								() -> serializeMetadata(new DiskMetadata(metadata.size(),
										BooleanArrayList.wrap(new boolean[DiskMetadata.getBlocksCount(metadata.size(), BLOCK_SIZE)])
								))
						),
						UpdateReturnMode.NOTHING
				)
				.then();
	}

	private <T extends URL> Buffer serializeUrl(T url) {
		@SuppressWarnings("unchecked")
		URLSerializer<T> urlSerializer = (URLSerializer<T>) url.getSerializer();

		int sizeHint = urlSerializer.getSerializedSizeHint();
		if (sizeHint == -1) sizeHint = 64;
		var buffer = db.getAllocator().allocate(sizeHint);
		try {
			try {
				urlSerializer.serialize(url, buffer);
			} catch (SerializationException ex) {
				throw new IllegalStateException("Failed to serialize url", ex);
			}
			return buffer;
		} catch (Throwable ex) {
			buffer.close();
			throw ex;
		}
	}

	private Buffer serializeMetadata(DiskMetadata diskMetadata) {
		int sizeHint = diskMetadataSerializer.getSerializedSizeHint();
		if (sizeHint == -1) sizeHint = 64;
		var buffer = db.getAllocator().allocate(sizeHint);
		try {
			try {
				diskMetadataSerializer.serialize(diskMetadata, buffer);
			} catch (SerializationException ex) {
				throw new IllegalStateException("Failed to serialize metadata", ex);
			}
			return buffer;
		} catch (Throwable ex) {
			buffer.close();
			throw ex;
		}
	}

	private DiskMetadata deserializeMetadata(Buffer prevBytes) {
		try {
			return diskMetadataSerializer.deserialize(prevBytes);
		} catch (SerializationException ex) {
			throw new IllegalStateException("Failed to deserialize metadata", ex);
		}
	}

	@Override
	public Mono<Void> writeContentBlock(URL url, DataBlock dataBlock) {
		// Check if this cache should cache the url, otherwise do nothing
		if (!shouldCache.test(url)) return Mono.empty();

		Mono<Buffer> urlKeyMono = Mono.fromCallable(() -> serializeUrl(url));
		Mono<Buffer> blockKeyMono = Mono.fromCallable(() -> getBlockKey(url, dataBlock.getId()));
		return Mono.using(
						() -> BufSupplier.of(dataBlock::getDataCopy),
						bufSupplier -> fileContent
								.put(blockKeyMono, Mono.fromSupplier(bufSupplier::get), LLDictionaryResultType.VOID)
								.doOnNext(Resource::close)
								.then(),
						BufSupplier::close
				)
				.then(fileMetadata.update(urlKeyMono, prevBytes -> {
							@Nullable DiskMetadata result;
							if (prevBytes != null) {
								DiskMetadata prevMeta = deserializeMetadata(prevBytes);
								if (!prevMeta.isDownloadedBlock(dataBlock.getId())) {
									BooleanArrayList bal = prevMeta.downloadedBlocks().clone();
									if (prevMeta.size() == -1) {
										if (bal.size() > dataBlock.getId()) {
											bal.set(dataBlock.getId(), true);
										} else if (bal.size() == dataBlock.getId()) {
											bal.add(true);
										} else {
											throw new IndexOutOfBoundsException(
													"Trying to write a block too much far from the last block. Previous total blocks: "
															+ bal.size() + " Current block id: " + dataBlock.getId());
										}
									} else {
										bal.set(dataBlock.getId(), true);
									}
									result = new DiskMetadata(prevMeta.size(), bal);
								} else {
									result = prevMeta;
								}
							} else {
								result = null;
							}
							if (result != null) {
								return serializeMetadata(result);
							} else {
								return null;
							}
						}, UpdateReturnMode.NOTHING)
				)
				.then();
	}

	@Override
	public Flux<DataBlock> requestContent(URL url) {
		return this
				.requestDiskMetadata(url)
				.filter(DiskMetadata::isDownloadedFully)
				.flatMapMany(meta -> Flux.fromStream(meta.downloadedBlocks()::stream)
						.index()
						// Get only downloaded blocks
						.filter(Tuple2::getT2)
						.flatMapSequential(blockMeta -> {
							int blockId = Math.toIntExact(blockMeta.getT1());
							boolean downloaded = blockMeta.getT2();
							if (!downloaded) {
								return Mono.empty();
							}
							var blockKeyMono = Mono.fromCallable(() -> getBlockKey(url, blockId));
							return fileContent
									.get(null, blockKeyMono)
									.map(data -> {
										try (data) {
											int blockOffset = getBlockOffset(blockId);
											int blockLength = data.readableBytes();
											if (meta.size() != -1) {
												if (blockOffset + blockLength >= meta.size()) {
													if (blockOffset + blockLength > meta.size()) {
														throw new IllegalStateException("Overflowed data size");
													}
												} else {
													// Intermediate blocks must be of max size
													assert data.readableBytes() == BLOCK_SIZE;
												}
											}
											return DataBlock.of(blockOffset, blockLength, data.send());
										}
									});
						})
				);
	}

	private Buffer getBlockKey(URL url, int blockId) {
		try (var urlBytes = serializeUrl(url)) {
			Buffer blockIdBytes = this.db.getAllocator().allocate(Integer.BYTES);
			blockIdBytes.writeInt(blockId);
			return LLUtils.compositeBuffer(db.getAllocator(), urlBytes.send(), blockIdBytes.send());
		}
	}

	private static int getBlockOffset(int blockId) {
		return blockId * BLOCK_SIZE;
	}

	@Override
	public Mono<DiskMetadata> requestDiskMetadata(URL url) {
		Mono<Buffer> urlKeyMono = Mono.fromCallable(() -> serializeUrl(url));
		return fileMetadata
				.get(null, urlKeyMono)
				.map(prevBytes -> {
					try (prevBytes) {
						return deserializeMetadata(prevBytes);
					}
				});
	}

	@Override
	public Mono<Metadata> requestMetadata(URL url) {
		return requestDiskMetadata(url)
				.map(DiskMetadata::asMetadata);
	}

	@Override
	public Mono<Tuple2<Metadata, Flux<DataBlock>>> request(URL url) {
		Mono<Buffer> urlKeyMono = Mono.fromCallable(() -> serializeUrl(url));
		return Mono
				.using(
						() -> serializeUrl(url),
						key -> fileMetadata.get(null, urlKeyMono),
						Resource::close
				)
				.map(serialized -> {
					DiskMetadata diskMeta;
					try (serialized) {
						diskMeta = deserializeMetadata(serialized);
					}
					var meta = diskMeta.asMetadata();
					if (diskMeta.isDownloadedFully()) {
						return Tuples.of(meta, this.requestContent(url));
					} else {
						return Tuples.of(meta, Flux.empty());
					}
				});
	}

	public Mono<Void> close() {
		return db.close();
	}
}
