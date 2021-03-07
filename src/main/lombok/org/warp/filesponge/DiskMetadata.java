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

import it.cavallium.dbengine.database.serialization.Serializer;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import lombok.Data;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

@Data
public class DiskMetadata {

	/**
	 * -1 = unknown size
	 */
	private final int size;

	private final BooleanArrayList downloadedBlocks;

	private Boolean downloadedFully;

	public boolean isDownloadedFully() {
		if (downloadedFully == null) {
			// Ensure blocks count is valid by calling getBlocksCount()
			getBlocksCount();
			// It's fully downloaded if every block is true
			downloadedFully = !this.getDownloadedBlocks().contains(false);
		}
		return downloadedFully;
	}

	private int getBlocksCount() {
		var expectedBlocksCount = getBlocksCount(size, Web.BLOCK_SIZE);
		if (this.getDownloadedBlocks().size() != expectedBlocksCount) {
			throw new IllegalStateException("Blocks array length != expected blocks count");
		}
		return expectedBlocksCount;
	}

	public static int getBlocksCount(int size, int blockSize) {
		return (size + (blockSize - size % blockSize)) / blockSize;
	}

	public Metadata asMetadata() {
		return new Metadata(size);
	}

	public static class DiskMetadataSerializer implements Serializer<DiskMetadata, byte[]> {

		@SneakyThrows
		@Override
		public @NotNull DiskMetadata deserialize(byte @NotNull [] serialized) {
			var bais = new ByteArrayInputStream(serialized);
			var dis = new DataInputStream(bais);
			int size = dis.readInt();
			int blocksCount = getBlocksCount(size, Web.BLOCK_SIZE);
			boolean[] downloadedBlocks = new boolean[blocksCount];
			for (int i = 0; i < blocksCount; i++) {
				downloadedBlocks[i] = dis.readBoolean();
			}
			return new DiskMetadata(size, BooleanArrayList.wrap(downloadedBlocks, blocksCount));
		}

		@SneakyThrows
		@Override
		public byte @NotNull [] serialize(@NotNull DiskMetadata deserialized) {
			try (var bos = new ByteArrayOutputStream(Integer.BYTES * 2)) {
				try (var dos = new DataOutputStream(bos)) {
					dos.writeInt(deserialized.getSize());
					if (deserialized.getDownloadedBlocks().size() != deserialized.getBlocksCount()) {
						throw new IllegalStateException("Blocks array length != expected blocks count");
					}
					for (boolean downloadedBlock : deserialized.getDownloadedBlocks()) {
						dos.writeBoolean(downloadedBlock);
					}
				}
				return bos.toByteArray();
			}
		}

	}
}
