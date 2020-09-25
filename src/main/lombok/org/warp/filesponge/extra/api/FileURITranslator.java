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

package org.warp.filesponge.extra.api;

import java.util.Optional;
import java.util.OptionalLong;
import org.warp.filesponge.value.FileURI;

/**
 * Translate File URIs to "fileId" and back
 */
public interface FileURITranslator {

	Optional<FileURI> getURI(long fileId);

	OptionalLong getFileId(FileURI fileURI);

	/**
	 * @throws AlreadyAssignedException Throw if the uri has another fileId assigned
	 */
	void setFileId(FileURI fileURI, long fileId) throws AlreadyAssignedException;

	Optional<FileURI> delete(long fileId);

	OptionalLong delete(FileURI fileURI);

	void clear();
}
