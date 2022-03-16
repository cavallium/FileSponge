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
import it.cavallium.dbengine.database.serialization.BufferDataOutput;
import it.cavallium.dbengine.database.serialization.SerializationException;
import org.jetbrains.annotations.NotNull;

public abstract class URLStringSerializer<T extends URL> implements URLSerializer<T> {

	@Override
	public final void serialize(@NotNull T url, Buffer output) throws SerializationException {
		var string = this.serialize(url);
		var dataOut = new BufferDataOutput(output);
		dataOut.writeUTF(string);
	}

	public abstract @NotNull String serialize(@NotNull T url);

	@Override
	public int getSerializedSizeHint() {
		return 64;
	}
}
