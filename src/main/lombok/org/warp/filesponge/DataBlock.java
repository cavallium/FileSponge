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
import java.util.Objects;

public final class DataBlock {

	public DataBlock(int offset, int length, ByteBuf data) {
		try {
			this.offset = offset;
			this.length = length;
			this.data = data.retain();
		} finally {
			data.release();
		}
	}

	private final int offset;
	private final int length;
	private final ByteBuf data;

	public ByteBuf getData() {
		assert data.isReadable();
		return data.retain();
	}

	public int getId() {
		return offset / FileSponge.BLOCK_SIZE;
	}

	public int getOffset() {
		return this.offset;
	}

	public int getLength() {
		return this.length;
	}

	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof DataBlock)) {
			return false;
		}
		final DataBlock other = (DataBlock) o;
		if (this.getOffset() != other.getOffset()) {
			return false;
		}
		if (this.getLength() != other.getLength()) {
			return false;
		}
		final Object this$data = this.getData();
		final Object other$data = other.getData();
		if (!Objects.equals(this$data, other$data)) {
			return false;
		}
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + this.getOffset();
		result = result * PRIME + this.getLength();
		final Object $data = this.getData();
		result = result * PRIME + ($data == null ? 43 : $data.hashCode());
		return result;
	}

	public String toString() {
		return "DataBlock(offset=" + this.getOffset() + ", length=" + this.getLength() + ", data=" + this.getData() + ")";
	}

	public void retain() {
		this.data.retain();
	}

	public void release() {
		this.data.release();
	}
}
