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
import org.warp.filesponge.value.FileContent;
import org.warp.filesponge.value.FileType;
import org.warp.filesponge.value.FileURI;
import org.warp.filesponge.value.MirrorURI;
import reactor.core.publisher.Mono;

public interface FileStorage<FURI extends FileURI, FTYPE extends FileType, MURI extends MirrorURI, FC extends FileContent> {

	Mono<Void> newFile(@NotNull FURI fileURI, @NotNull FTYPE fileType);

	Mono<FC> readFileData(@NotNull FURI fileURI);

	Mono<Void> setFileData(@NotNull FURI fileURI, long offset, long size, ByteBuffer bytes, long totalSize);

	Mono<Boolean> hasAllData(@NotNull FURI fileURI);
}
