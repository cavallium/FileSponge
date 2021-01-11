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

import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.warp.filesponge.value.AsyncMultiAssociation;
import org.warp.filesponge.value.FileURI;
import org.warp.filesponge.value.MirrorURI;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@AllArgsConstructor()
public class FileMirrorsManager {

	private final Scheduler fileMirrorsManagerScheduler = Schedulers.single();

	private final MirrorAvailabilityManager mirrorAvailabilityManager;

	/**
	 * This map must be persistent
	 */
	private final AsyncMultiAssociation<FileURI, MirrorURI> fileMirrors;

	public Mono<Set<MirrorURI>> getAvailableMirrors(FileURI fileURI) {
		return fileMirrors
				.getLinks(fileURI)
				.filterWhen(mirrorAvailabilityManager::isMirrorAvailable)
				.collect(Collectors.toUnmodifiableSet())
				.subscribeOn(fileMirrorsManagerScheduler);
	}

	public Mono<Boolean> hasAnyAvailableMirror(FileURI uri) {
		return fileMirrors
				.getLinks(uri)
				.filterWhen(mirrorAvailabilityManager::isMirrorAvailable)
				.hasElements()
				.subscribeOn(fileMirrorsManagerScheduler);
	}

	public Mono<Void> addMirror(FileURI uri, MirrorURI mirrorURI) {
		return fileMirrors
				.link(uri, mirrorURI)
				.then()
				.subscribeOn(fileMirrorsManagerScheduler);
	}

	public Mono<Void> removeMirror(FileURI uri, MirrorURI mirrorURI) {
		return fileMirrors
				.unlink(uri, mirrorURI)
				.then()
				.subscribeOn(fileMirrorsManagerScheduler);
	}

	public Mono<Void> unsetAllFiles() {
		return fileMirrors.clear()
				.subscribeOn(fileMirrorsManagerScheduler);
	}
}
