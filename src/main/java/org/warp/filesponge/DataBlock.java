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
import io.netty5.buffer.api.Drop;
import io.netty5.buffer.api.Owned;
import io.netty5.util.Send;
import io.netty5.buffer.api.internal.ResourceSupport;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DataBlock extends ResourceSupport<DataBlock, DataBlock> {

	private static final Logger logger = LogManager.getLogger(DataBlock.class);

	private static final Drop<DataBlock> DROP = new Drop<>() {
		@Override
		public void drop(DataBlock obj) {
			try {
				if (obj.data != null) {
					obj.data.close();
				}
			} catch (Throwable ex) {
				logger.error("Failed to close data", ex);
			}
		}

		@Override
		public Drop<DataBlock> fork() {
			return this;
		}

		@Override
		public void attach(DataBlock obj) {

		}
	};

	public static DataBlock of(long offset, int length, Send<Buffer> data) {
		return new DataBlock(offset, length, data);
	}

	private DataBlock(long offset, int length, Send<Buffer> data) {
		super(DROP);
		try (data) {
			this.offset = offset;
			this.length = length;
			this.data = data.receive();
		}
	}

	private final long offset;
	private final int length;
	private final Buffer data;

	public Buffer getDataCopy() {
		assert data.isAccessible();
		return data.copy();
	}

	public Buffer getDataUnsafe() {
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
		final Object this$data = this.getDataUnsafe();
		final Object other$data = other.getDataUnsafe();
		if (!Objects.equals(this$data, other$data)) {
			return false;
		}
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		long offset = this.getOffset();
		result = result * PRIME + (int) (offset ^ (offset >>> 32));
		result = result * PRIME + this.getLength();
		final Object $data = this.getDataUnsafe();
		result = result * PRIME + ($data == null ? 43 : $data.hashCode());
		return result;
	}

	public String toString() {
		return "DataBlock(offset=" + this.getOffset() + ", length=" + this.getLength() + ", data=" + this.getDataUnsafe() + ")";
	}

	@Override
	protected RuntimeException createResourceClosedException() {
		return new IllegalStateException("Closed");
	}

	@Override
	protected Owned<DataBlock> prepareSend() {
		Send<Buffer> dataSend;
		dataSend = this.data.send();
		return drop -> {
			var instance = new DataBlock(offset, length, dataSend);
			drop.attach(instance);
			return instance;
		};
	}
}
