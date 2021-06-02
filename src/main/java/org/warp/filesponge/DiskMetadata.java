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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import it.cavallium.dbengine.database.serialization.Serializer;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.apache.commons.lang3.SerializationException;
import org.jetbrains.annotations.NotNull;

/**
 * size -1 = unknown size
 */
public record DiskMetadata(int size, BooleanArrayList downloadedBlocks) {

	public boolean isDownloadedFully() {
		boolean downloadedFullyVal;
		// Ensure blocks count is valid by calling getBlocksCount()
		getBlocksCount();
		// It's fully downloaded if every block is true
		downloadedFullyVal = !this.downloadedBlocks().contains(false);
		return downloadedFullyVal;
	}

	@SuppressWarnings("UnusedReturnValue")
	private int getBlocksCount() {
		if (size == -1) {
			return downloadedBlocks().size();
		}
		var expectedBlocksCount = getBlocksCount(size, FileSponge.BLOCK_SIZE);
		if (this.downloadedBlocks().size() != expectedBlocksCount) {
			throw new IllegalStateException(
					"Blocks array length (" + this.downloadedBlocks().size() + ") != expected blocks count ("
							+ expectedBlocksCount + ")");
		}
		return expectedBlocksCount;
	}

	public static int getBlocksCount(int size, int blockSize) {
		if (size == -1) {
			return 0;
		}
		return (size + (blockSize - size % blockSize)) / blockSize;
	}

	public Metadata asMetadata() {
		return new Metadata(size);
	}

	public boolean isDownloadedBlock(int id) {
		if (size == -1 && downloadedBlocks().size() <= id) {
			return false;
		} else {
			return downloadedBlocks().getBoolean(id);
		}
	}

	public static class DiskMetadataSerializer implements Serializer<DiskMetadata, ByteBuf> {

		private final ByteBufAllocator allocator;

		public DiskMetadataSerializer(ByteBufAllocator allocator) {
			this.allocator = allocator;
		}

		@Override
		public @NotNull DiskMetadata deserialize(@NotNull ByteBuf serialized) {
			try {
				var bais = new ByteBufInputStream(serialized);
				var dis = new DataInputStream(bais);
				int size = dis.readInt();
				int blocksCount;
				if (size == -1) {
					blocksCount = dis.readShort();
				} else {
					blocksCount = getBlocksCount(size, FileSponge.BLOCK_SIZE);
				}
				var downloadedBlocks = new BooleanArrayList(blocksCount);
				for (int i = 0; i < blocksCount; i++) {
					downloadedBlocks.add(dis.readBoolean());
				}
				return new DiskMetadata(size, downloadedBlocks);
			} catch (IOException e) {
				throw new SerializationException(e);
			} finally {
				serialized.release();
			}
		}

		@Override
		public @NotNull ByteBuf serialize(@NotNull DiskMetadata deserialized) {
			ByteBuf buffer = allocator.buffer();
			try (var bos = new ByteBufOutputStream(buffer)) {
				try (var dos = new DataOutputStream(bos)) {
					dos.writeInt(deserialized.size());
					if (deserialized.size == -1) {
						dos.writeShort(deserialized.getBlocksCount());
					} else {
						deserialized.getBlocksCount();
					}
					for (boolean downloadedBlock : deserialized.downloadedBlocks()) {
						dos.writeBoolean(downloadedBlock);
					}
				}
				return buffer;
			} catch (IOException e) {
				throw new SerializationException(e);
			}
		}

	}
}
