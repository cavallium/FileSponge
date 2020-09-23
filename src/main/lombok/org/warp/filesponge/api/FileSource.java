/*
 *     FileSponge
 *     Copyright (C) 2020 Andrea Cavalli
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

package org.warp.filesponge.api;

import java.nio.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public interface FileSource {

	void onAvailable();

	void onUnavailable();

	boolean onNewFile(@NotNull FileURI fileURI, @NotNull FileExtension fileExtension);

	void onFile(@NotNull FileURI fileURI, @NotNull FileSourceAvailability fileAvailability, long totalSize);

	void onFilePiece(@NotNull FileURI fileURI, long offset, long size, @NotNull ByteBuffer piece);
}
