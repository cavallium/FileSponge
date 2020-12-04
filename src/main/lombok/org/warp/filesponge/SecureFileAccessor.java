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

package org.warp.filesponge;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import org.warp.filesponge.api.FileAccessor;
import org.warp.filesponge.value.FileContent;
import org.warp.filesponge.value.FileStatus;
import org.warp.filesponge.value.FileURI;

/**
 * Prevent access to other methods via casting
 */
@AllArgsConstructor
@EqualsAndHashCode
public class SecureFileAccessor<FURI extends FileURI, FC extends FileContent> implements FileAccessor<FURI, FC> {

	private final FileAccessor<FURI, FC> fileAccessor;

	@Override
	public void delete(@NotNull FURI fileURI) {
		fileAccessor.delete(fileURI);
	}

	@Override
	public CompletionStage<Optional<FC>> getContent(@NotNull FURI fileURI, boolean offlineOnly) {
		return fileAccessor.getContent(fileURI, offlineOnly);
	}

	@Override
	public @NotNull FileStatus getStatus(@NotNull FURI fileURI) {
		return fileAccessor.getStatus(fileURI);
	}

	@Override
	public String toString() {
		return fileAccessor.toString();
	}
}
