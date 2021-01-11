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
import org.jetbrains.annotations.Nullable;
import org.warp.filesponge.value.FileType;
import org.warp.filesponge.value.FileURI;
import org.warp.filesponge.value.MirrorURI;
import reactor.core.publisher.Mono;

public interface FileStorage<FURI extends FileURI, FTYPE extends FileType, MURI extends MirrorURI> {

	Mono<Void> newFile(@NotNull FURI fileURI, @NotNull FTYPE fileType);

	Mono<ByteBuffer> readFileData(@NotNull FURI fileURI);

	/**
	 * Set a part of file data.
	 * If file size is 0, the file will be deleted.
	 * @param fileURI File URI
	 * @param offset offset of the current data segment
	 * @param size current data segment size
	 * @param bytes data segment, can be null if totalSize is 0
	 * @param totalSize total file size
	 * @return nothing
	 */
	Mono<Void> setFileData(@NotNull FURI fileURI, long offset, long size, @Nullable ByteBuffer bytes, long totalSize);

	Mono<Boolean> hasAllData(@NotNull FURI fileURI);

	/**
	 * Delete a file
	 * @param fileURI
	 * @return
	 */
	default Mono<Void> deleteFile(@NotNull FURI fileURI) {
		return setFileData(fileURI, 0, 0, null, 0);
	}
}
