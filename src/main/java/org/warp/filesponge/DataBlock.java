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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Drop;
import io.netty5.buffer.api.Owned;
import io.netty5.buffer.api.Send;
import io.netty5.buffer.api.internal.ResourceSupport;
import java.util.Objects;

public final class DataBlock extends ResourceSupport<DataBlock, DataBlock> {

	public static DataBlock of(int offset, int length, Send<Buffer> data) {
		return new DataBlock(offset, length, data, d -> {});
	}

	private DataBlock(int offset, int length, Send<Buffer> data, Drop<DataBlock> drop) {
		super(new DataBlock.CloseOnDrop(drop));
		try (data) {
			this.offset = offset;
			this.length = length;
			this.data = data.receive();
		}
	}

	private final int offset;
	private final int length;
	private final Buffer data;

	public Send<Buffer> getData() {
		assert data.isAccessible();
		return data.copy().send();
	}

	public Buffer getDataUnsafe() {
		return data;
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

	@Override
	protected RuntimeException createResourceClosedException() {
		return new IllegalStateException("Closed");
	}

	@Override
	protected Owned<DataBlock> prepareSend() {
		Send<Buffer> dataSend;
		dataSend = this.data.send();
		return drop -> new DataBlock(offset, length, dataSend, drop);
	}

	private static class CloseOnDrop implements Drop<DataBlock> {

		private final Drop<DataBlock> delegate;

		public CloseOnDrop(Drop<DataBlock> drop) {
			this.delegate = drop;
		}

		@Override
		public void drop(DataBlock obj) {
			if (obj.data.isAccessible()) {
				obj.data.close();
			}
			delegate.drop(obj);
		}
	}
}
