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

import it.cavallium.buffer.Buf;
import it.cavallium.buffer.BufDataOutput;
import it.cavallium.dbengine.database.serialization.SerializationException;
import org.jetbrains.annotations.NotNull;

public interface URLSerializer<T extends URL> {

	/**
	 * @param output its writable size will be at least equal to the size hint
	 */
	void serialize(@NotNull T url, BufDataOutput output) throws SerializationException;

	/**
	 * @return hint about the expected size of the buffer
	 */
	int getSerializedSizeHint();
}
