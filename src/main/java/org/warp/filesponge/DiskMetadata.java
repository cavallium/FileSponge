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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.util.Send;
import it.cavallium.dbengine.database.serialization.BufferDataInputOwned;
import it.cavallium.dbengine.database.serialization.BufferDataInputShared;
import it.cavallium.dbengine.database.serialization.BufferDataOutput;
import it.cavallium.dbengine.database.serialization.SerializationException;
import it.cavallium.dbengine.database.serialization.Serializer;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * size -1 = unknown size
 */
public record DiskMetadata(long size, BooleanArrayList downloadedBlocks) {

	public boolean isDownloadedFully() {
		boolean downloadedFullyVal;
		// Ensure blocks count is valid by calling getBlocksCount()
		getBlocksCount();
		// It's fully downloaded if every block is true
		downloadedFullyVal = !this.downloadedBlocks.contains(false);
		return downloadedFullyVal;
	}

	@SuppressWarnings("UnusedReturnValue")
	private int getBlocksCount() {
		if (size == -1) {
			return downloadedBlocks.size();
		}
		var expectedBlocksCount = getBlocksCount(size, FileSponge.BLOCK_SIZE);
		if (this.downloadedBlocks.size() != expectedBlocksCount) {
			throw new IllegalStateException(
					"Blocks array length (" + this.downloadedBlocks.size() + ") != expected blocks count ("
							+ expectedBlocksCount + ")");
		}
		return expectedBlocksCount;
	}

	public static int getBlocksCount(long size, int blockSize) {
		if (size == -1L) {
			return 0;
		}
		return toIntExact((size + (blockSize - size % blockSize)) / blockSize);
	}

	public Metadata asMetadata() {
		return new Metadata(size);
	}

	public boolean isDownloadedBlock(int id) {
		if (size == -1 && downloadedBlocks.size() <= id) {
			return false;
		} else {
			return downloadedBlocks.getBoolean(id);
		}
	}

	public static class DiskMetadataSerializer implements Serializer<DiskMetadata> {

		@Override
		public @NotNull DiskMetadata deserialize(@NotNull Buffer serialized) throws SerializationException {
			var dis = new BufferDataInputShared(serialized);
			int legacySize = dis.readInt();
			long size;
			if (legacySize == -2) {
				size = dis.readLong();
			} else {
				size = legacySize;
			}
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
		}

		@Override
		public void serialize(@NotNull DiskMetadata deserialized, Buffer output) throws SerializationException {
			var dos = new BufferDataOutput(output);
			dos.writeInt(-2);
			dos.writeLong(deserialized.size);
			var blocksCount = deserialized.getBlocksCount();
			if (deserialized.size == -1) {
				dos.writeShort(blocksCount);
			}
			for (boolean downloadedBlock : deserialized.downloadedBlocks) {
				dos.writeBoolean(downloadedBlock);
			}
		}

		@Override
		public int getSerializedSizeHint() {
			return Integer.BYTES + Long.BYTES;
		}
	}
}
