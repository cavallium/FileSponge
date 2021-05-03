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

import com.google.common.primitives.Ints;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCounted;
import it.cavallium.dbengine.database.Column;
import it.cavallium.dbengine.database.LLDatabaseConnection;
import it.cavallium.dbengine.database.LLDictionary;
import it.cavallium.dbengine.database.LLDictionaryResultType;
import it.cavallium.dbengine.database.LLKeyValueDatabase;
import it.cavallium.dbengine.database.UpdateMode;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.warp.filesponge.DiskMetadata.DiskMetadataSerializer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DiskCache implements URLsDiskHandler, URLsWriter {

	private static final DiskMetadataSerializer diskMetadataSerializer = new DiskMetadataSerializer();

	private final LLKeyValueDatabase db;
	private final LLDictionary fileContent;
	private final LLDictionary fileMetadata;

	public static Mono<DiskCache> open(LLDatabaseConnection databaseConnection, String dbName, boolean lowMemory) {
		return databaseConnection
				.getDatabase(dbName, List.of(Column.dictionary("file-content"), Column.dictionary("file-metadata")), lowMemory, false)
				.flatMap(db -> Mono.zip(
						Mono.just(db).single(),
						db.getDictionary("file-content", UpdateMode.ALLOW).single(),
						db.getDictionary("file-metadata", UpdateMode.ALLOW).single()
				))
				.map(tuple -> new DiskCache(tuple.getT1(), tuple.getT2(), tuple.getT3()))
				.single();
	}

	@Override
	public Mono<Void> writeMetadata(URL url, Metadata metadata) {
		return fileMetadata
				.update(url.getSerializer().serialize(url), oldValue -> {
					if (oldValue != null) {
						return oldValue;
					} else {
						return diskMetadataSerializer.serialize(new DiskMetadata(
										metadata.getSize(),
										BooleanArrayList.wrap(new boolean[DiskMetadata.getBlocksCount(metadata.getSize(), BLOCK_SIZE)])
								));
					}
				})
				.then();
	}

	@Override
	public Mono<Void> writeContentBlock(URL url, DataBlock dataBlock) {
		return Mono
				.fromCallable(() -> {
					return dataBlock.getData();
					/*
					ByteBuf bytes = PooledByteBufAllocator.DEFAULT.directBuffer(dataBlock.getLength());
					bytes.writeBytes(dataBlock.getData().slice());
					return bytes;

					 */
				})
				.subscribeOn(Schedulers.boundedElastic())
				.flatMap(bytes -> fileContent.put(getBlockKey(url, dataBlock.getId()), bytes, LLDictionaryResultType.VOID))
				.doOnNext(ReferenceCounted::release)
				.then(fileMetadata.update(url.getSerializer().serialize(url), prevBytes -> {
					@Nullable DiskMetadata result;
					if (prevBytes != null) {
						DiskMetadata prevMeta = diskMetadataSerializer.deserialize(prevBytes);
						if (!prevMeta.getDownloadedBlocks().getBoolean(dataBlock.getId())) {
							BooleanArrayList bal = prevMeta.getDownloadedBlocks().clone();
							bal.set(dataBlock.getId(), true);
							result = new DiskMetadata(prevMeta.getSize(), bal);
						} else {
							result = prevMeta;
						}
					} else {
						result = null;
					}
					if (result != null) {
						return diskMetadataSerializer.serialize(result);
					} else {
						return null;
					}
				}))
				.then();
	}

	@Override
	public Flux<DataBlock> requestContent(URL url) {
		return requestDiskMetadata(url)
				.filter(DiskMetadata::isDownloadedFully)
				.flatMapMany(meta -> Flux.fromIterable(meta.getDownloadedBlocks())
						.index()
						// Get only downloaded blocks
						.filter(Tuple2::getT2)
						.flatMapSequential(blockMeta -> {
							int blockId = Math.toIntExact(blockMeta.getT1());
							boolean downloaded = blockMeta.getT2();
							if (!downloaded) {
								return Mono.<DataBlock>empty();
							}
							return fileContent.get(null, getBlockKey(url, blockId)).map(data -> {
								int blockOffset = getBlockOffset(blockId);
								int blockLength = data.readableBytes();
								if (blockOffset + blockLength >= meta.getSize()) {
									if (blockOffset + blockLength > meta.getSize()) {
										throw new IllegalStateException("Overflowed data size");
									}
								} else {
									// Intermediate blocks must be of max size
									assert data.readableBytes() == BLOCK_SIZE;
								}
								return new DataBlock(blockOffset, blockLength, data);
							});
						}));
	}

	private ByteBuf getBlockKey(URL url, int blockId) {
		ByteBuf urlBytes = url.getSerializer().serialize(url);
		ByteBuf blockIdBytes = PooledByteBufAllocator.DEFAULT.directBuffer(Integer.BYTES, Integer.BYTES);
		blockIdBytes.writeInt(blockId);
		return Unpooled.wrappedBuffer(urlBytes, blockIdBytes);
	}

	private static int getBlockOffset(int blockId) {
		return blockId * BLOCK_SIZE;
	}

	@Override
	public Mono<DiskMetadata> requestDiskMetadata(URL url) {
		return fileMetadata
				.get(null, url.getSerializer().serialize(url))
				.map(diskMetadataSerializer::deserialize);
	}

	@Override
	public Mono<Metadata> requestMetadata(URL url) {
		return requestDiskMetadata(url)
				.map(DiskMetadata::asMetadata);
	}

	@Override
	public Mono<Tuple2<Metadata, Flux<DataBlock>>> request(URL url) {
		return fileMetadata
				.get(null, url.getSerializer().serialize(url))
				.map(diskMetadataSerializer::deserialize)
				.map(diskMeta -> {
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
