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

import it.cavallium.buffer.Buf;
import java.nio.Buffer;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DataBlock {

	public static DataBlock EMPTY = new DataBlock(-1, -1, null);

	public static DataBlock of(long offset, int length, Buf data) {
		return new DataBlock(offset, length, data);
	}

	private DataBlock(long offset, int length, Buf data) {
		this.offset = offset;
		this.length = length;
		this.data = data;
	}

	private final long offset;
	private final int length;
	private final Buf data;

	public Buf getData() {
		return data;
	}

	public int getId() {
		return toIntExact(offset / FileSponge.BLOCK_SIZE);
	}

	public long getOffset() {
		return this.offset;
	}

	public int getLength() {
		return this.length;
	}

	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof final DataBlock other)) {
			return false;
		}
		if (this.getOffset() != other.getOffset()) {
			return false;
		}
		if (this.getLength() != other.getLength()) {
			return false;
		}
		final Object this$data = this.getData();
		final Object other$data = other.getData();
		return Objects.equals(this$data, other$data);
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		long offset = this.getOffset();
		result = result * PRIME + (int) (offset ^ (offset >>> 32));
		result = result * PRIME + this.getLength();
		final Object $data = this.getData();
		result = result * PRIME + ($data == null ? 43 : $data.hashCode());
		return result;
	}

	public String toString() {
		return "DataBlock(offset=" + this.getOffset() + ", length=" + this.getLength() + ", data=" + this.getData() + ")";
	}

}
