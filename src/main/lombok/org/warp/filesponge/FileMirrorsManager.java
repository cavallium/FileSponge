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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.warp.commonutils.type.MultiAssociation;
import org.warp.filesponge.value.FileURI;
import org.warp.filesponge.value.MirrorURI;

@AllArgsConstructor()
public class FileMirrorsManager {

	private final Set<MirrorURI> availableMirrors = new HashSet<>();

	/**
	 * This map must be persistent
	 */
	private final MultiAssociation<FileURI, MirrorURI> fileMirrors;

	public synchronized Set<MirrorURI> getAvailableMirrors(FileURI fileURI) {
		return fileMirrors
				.getLinks(fileURI)
				.stream()
				.filter(this::isMirrorAvailable)
				.collect(Collectors.toUnmodifiableSet());
	}

	public synchronized boolean hasAnyAvailableReceiver(FileURI uri) {
		return fileMirrors.getLinks(uri).stream().anyMatch(this::isMirrorAvailable);
	}

	public synchronized void addMirror(FileURI uri, MirrorURI mirrorURI) {
		fileMirrors.link(uri, mirrorURI);
	}

	public synchronized void removeMirror(FileURI uri, MirrorURI mirrorURI) {
		fileMirrors.unlink(uri, mirrorURI);
	}

	public synchronized void unsetAllFiles() {
		fileMirrors.clear();
	}

	public synchronized void setAllMirrorsAsUnavailable() {
		availableMirrors.clear();
	}

	public synchronized void setReceiverAvailability(MirrorURI mirrorURI, boolean available) {
		if (available) {
			availableMirrors.add(mirrorURI);
		} else {
			availableMirrors.remove(mirrorURI);
		}
	}

	public synchronized boolean isMirrorAvailable(MirrorURI mirrorURI) {
		return this.availableMirrors.contains(mirrorURI);
	}
}