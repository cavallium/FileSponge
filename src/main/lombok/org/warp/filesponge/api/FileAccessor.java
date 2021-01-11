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

import org.jetbrains.annotations.NotNull;
import org.warp.filesponge.value.FileContent;
import org.warp.filesponge.value.FileStatus;
import org.warp.filesponge.value.FileURI;
import reactor.core.publisher.Mono;

/**
 * FileAccessor can be used to access files from the client side
 */
public interface FileAccessor<FURI extends FileURI, FC extends FileContent> {

	/**
	 * Request file deletion
	 *
	 * @param fileURI File URI
	 * @return Empty.
	 */
	Mono<Void> delete(@NotNull FURI fileURI);

	/**
	 * Get file content
	 *
	 * @param fileURI     File URI
	 * @param offlineOnly true to get the file from cache
	 * @return content if found. If the request is offline the future will complete instantly.
	 * Can be empty
	 */
	Mono<FC> getContent(@NotNull FURI fileURI, boolean offlineOnly);

	/**
	 * Get file status
	 *
	 * @param fileURI File URI
	 * @return status of this file. Cannot be empty.
	 */
	Mono<FileStatus> getStatus(@NotNull FURI fileURI);
}
