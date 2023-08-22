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

import static java.lang.Math.toIntExact;
import static org.warp.filesponge.FileSponge.BLOCK_SIZE;

import it.cavallium.buffer.Buf;
import it.cavallium.buffer.BufDataInput;
import it.cavallium.buffer.BufDataOutput;
import it.cavallium.dbengine.database.LLDictionary;
import it.cavallium.dbengine.database.LLDictionaryResultType;
import it.cavallium.dbengine.database.LLKeyValueDatabase;
import it.cavallium.dbengine.database.UpdateReturnMode;
import it.cavallium.dbengine.database.serialization.SerializationException;
import it.cavallium.dbengine.utils.StreamUtils;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;
import org.warp.filesponge.DiskMetadata.DiskMetadataSerializer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

class DiskCacheImpl implements DiskCache {

	private final DiskMetadataSerializer diskMetadataSerializer;
	@Nullable
	private final LLKeyValueDatabase ownedDb;
	private final LLDictionary fileContent;
	private final LLDictionary fileMetadata;
	private final Predicate<URL> shouldCache;

	DiskCacheImpl(@Nullable LLKeyValueDatabase ownedDb,
			LLDictionary fileContent,
			LLDictionary fileMetadata,
			Predicate<URL> shouldCache) {
		this.ownedDb = ownedDb;
		this.fileContent = fileContent;
		this.fileMetadata = fileMetadata;
		this.diskMetadataSerializer = new DiskMetadataSerializer();
		this.shouldCache = shouldCache;
	}

	@Override
	public Mono<Void> writeMetadata(URL url, Metadata metadata, boolean force) {
		return Mono.<Void>fromRunnable(() -> writeMetadataSync(url, metadata, force)).subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public void writeMetadataSync(URL url, Metadata metadata, boolean force) {
		// Check if this cache should cache the url, otherwise do nothing
		if (!shouldCache.test(url)) return;

		var key = serializeUrl(url);

		fileMetadata.update(key, oldValue -> {
			if (oldValue != null) {
				return oldValue;
			} else {
				return serializeMetadata(new DiskMetadata(metadata.size(),
						BooleanArrayList.wrap(new boolean[DiskMetadata.getBlocksCount(metadata.size(), BLOCK_SIZE)])
				));
			}
		}, UpdateReturnMode.NOTHING);
	}

	private <T extends URL> Buf serializeUrl(T url) {
		@SuppressWarnings("unchecked")
		URLSerializer<T> urlSerializer = (URLSerializer<T>) url.getSerializer();

		int sizeHint = urlSerializer.getSerializedSizeHint();
		if (sizeHint == -1) sizeHint = 64;
		var output = BufDataOutput.create(sizeHint);
		try {
			urlSerializer.serialize(url, output);
		} catch (SerializationException ex) {
			throw new IllegalStateException("Failed to serialize url", ex);
		}
		return output.asList();
	}

	private Buf serializeMetadata(DiskMetadata diskMetadata) {
		int sizeHint = diskMetadataSerializer.getSerializedSizeHint();
		if (sizeHint == -1) sizeHint = 64;
		var out = BufDataOutput.create(sizeHint);
		try {
			diskMetadataSerializer.serialize(diskMetadata, out);
		} catch (SerializationException ex) {
			throw new IllegalStateException("Failed to serialize metadata", ex);
		}
		return out.asList();
	}

	private DiskMetadata deserializeMetadata(Buf prevBytes) {
		try {
			return diskMetadataSerializer.deserialize(BufDataInput.create(prevBytes));
		} catch (SerializationException ex) {
			throw new IllegalStateException("Failed to deserialize metadata", ex);
		}
	}

	@Override
	public Mono<Void> writeContentBlock(URL url, DataBlock dataBlock, boolean force) {
		return Mono
				.<Void>fromRunnable(() -> writeContentBlockSync(url, dataBlock, force))
				.subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public void writeContentBlockSync(URL url, DataBlock dataBlock, boolean force) {
		// Check if this cache should cache the url, otherwise do nothing
		if (!shouldCache.test(url)) {
			return;
		}

		Buf urlKey = serializeUrl(url);
		Buf blockKey = getBlockKey(url, dataBlock.getId());

		fileContent.put(blockKey, dataBlock.getData(), LLDictionaryResultType.VOID);
		fileMetadata.update(urlKey, prevBytes -> {
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
		}, UpdateReturnMode.NOTHING);
	}

	@Override
	public Flux<DataBlock> requestContent(URL url) {
		return Flux.fromStream(() -> requestContentSync(url)).subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Stream<DataBlock> requestContentSync(URL url) {
		record BlockMeta(int blockId, boolean downloaded) {}
		var meta = this.requestDiskMetadataSync(url);
		if (meta == null || !meta.isDownloadedFully()) {
			return Stream.empty();
		}
		return StreamUtils.indexed(meta.downloadedBlocks().stream(),
						(downloaded, blockId) -> new BlockMeta(toIntExact(blockId), downloaded)
				)
				// Get only downloaded blocks
				.filter(BlockMeta::downloaded).map(blockMeta -> {
					if (!blockMeta.downloaded) {
						return null;
					}
					var blockKey = getBlockKey(url, blockMeta.blockId);
					var data = fileContent.get(null, blockKey);
					long blockOffset = getBlockOffset(blockMeta.blockId);
					int blockLength = data.size();
					if (meta.size() != -1) {
						if (blockOffset + blockLength >= meta.size()) {
							if (blockOffset + blockLength > meta.size()) {
								throw new IllegalStateException("Overflowed data size");
							}
						} else {
							// Intermediate blocks must be of max size
							assert data.size() == BLOCK_SIZE;
						}
					}
					return DataBlock.of(blockOffset, blockLength, data);
				}).filter(Objects::nonNull);
	}

	private <T extends URL> Buf getBlockKey(T url, int blockId) {
		//noinspection unchecked
		URLSerializer<T> urlSerializer = (URLSerializer<T>) url.getSerializer();

		int urlSizeHint = urlSerializer.getSerializedSizeHint();
		if (urlSizeHint == -1) {
			urlSizeHint = 64;
		}

		var sizeHint = urlSizeHint + Integer.BYTES;
		var out = BufDataOutput.create(sizeHint);

		try {
			urlSerializer.serialize(url, out);
		} catch (SerializationException ex) {
			throw new IllegalStateException("Failed to serialize url", ex);
		}

		out.writeInt(blockId);

		return out.asList();
	}

	private static long getBlockOffset(int blockId) {
		return blockId * (long) BLOCK_SIZE;
	}

	@Override
	public Mono<DiskMetadata> requestDiskMetadata(URL url) {
		return Mono.fromCallable(() -> requestDiskMetadataSync(url)).subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public DiskMetadata requestDiskMetadataSync(URL url) {
		Buf urlKey = serializeUrl(url);
		var prevBytes = fileMetadata.get(null, urlKey);
		if (prevBytes != null) {
			return deserializeMetadata(prevBytes);
		} else {
			return null;
		}
	}

	@Override
	public Mono<Metadata> requestMetadata(URL url) {
		return requestDiskMetadata(url).map(DiskMetadata::asMetadata);
	}

	@Override
	public Metadata requestMetadataSync(URL url) {
		var metadata = requestDiskMetadataSync(url);
		if (metadata != null) {
			return metadata.asMetadata();
		} else {
			return null;
		}
	}

	@Override
	public Mono<Tuple2<Metadata, Flux<DataBlock>>> request(URL url) {
		return Mono
				.fromCallable(() -> {
					var tuple = requestSync(url);
					if (tuple == null) {
						return null;
					}
					return tuple.mapT2(s -> Flux.fromStream(s).subscribeOn(Schedulers.boundedElastic()));
				})
				.subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Tuple2<Metadata, Stream<DataBlock>> requestSync(URL url) {
		Buf urlKey = serializeUrl(url);
		var serialized = fileMetadata.get(null, urlKey);
		if (serialized == null) {
			return null;
		}
		DiskMetadata diskMeta = deserializeMetadata(serialized);
		var meta = diskMeta.asMetadata();
		if (diskMeta.isDownloadedFully()) {
			return Tuples.of(meta, this.requestContentSync(url));
		} else {
			return Tuples.of(meta, Stream.empty());
		}
	}

	@Override
	public void close() {
		if (ownedDb != null) {
			ownedDb.close();
		}
	}

	@Override
	public void pauseForBackup() {
		if (ownedDb != null) {
			ownedDb.pauseForBackup();
		}
	}

	@Override
	public void resumeAfterBackup() {
		if (ownedDb != null) {
			ownedDb.resumeAfterBackup();
		}
	}

	@Override
	public boolean isPaused() {
		if (ownedDb != null) {
			return ownedDb.isPaused();
		} else {
			return false;
		}
	}
}
