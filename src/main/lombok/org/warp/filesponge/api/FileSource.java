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
import org.warp.filesponge.value.FileSourceAvailability;
import org.warp.filesponge.value.FileType;
import org.warp.filesponge.value.FileURI;

/**
 * FileSource receives responses from a mirror
 */
public interface FileSource<FURI extends FileURI, FTYPE extends FileType> {

	/**
	 * Called when the mirror is online
	 */
	void onAvailable();

	/**
	 * Called when the mirror is unreachable
	 */
	void onUnavailable();

	/**
	 * Called when the mirror notifies you that a new file exists
	 */
	boolean onNewFile(@NotNull FURI fileURI, @NotNull FTYPE fileType);

	/**
	 * Called when the mirror notifies you details about a file.
	 * <p>
	 * {@link FileSource#onNewFile(FURI, FTYPE)} must have been already called
	 */
	void onFile(@NotNull FURI fileURI, @NotNull FileSourceAvailability fileAvailability, long totalSize);

	/**
	 * Called when the mirror notifies you the bytes of a part of a file.
	 * <p>
	 * {@link FileSource#onNewFile(FURI, FTYPE)} and {@link FileSource#onFile(FURI, FileSourceAvailability, long)} must
	 * have been already called
	 */
	void onFilePiece(@NotNull FURI fileURI, long offset, long size, @NotNull ByteBuffer piece);
}
