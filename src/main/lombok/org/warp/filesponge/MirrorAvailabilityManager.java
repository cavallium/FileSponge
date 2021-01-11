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

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.warp.filesponge.value.MirrorURI;
import org.warp.filesponge.reactor.ConcurrentAsyncSet;
import org.warp.filesponge.reactor.AsyncSet;
import reactor.core.publisher.Mono;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class MirrorAvailabilityManager {

	private final AsyncSet<MirrorURI> availableMirrors = new ConcurrentAsyncSet<>();

	public Mono<Void> setAllMirrorsAsUnavailable() {
		return availableMirrors.clear();
	}

	public Mono<Void> setMirrorAvailability(MirrorURI mirrorURI, boolean available) {
		if (available) {
			return availableMirrors.add(mirrorURI).then();
		} else {
			return availableMirrors.remove(mirrorURI).then();
		}
	}

	public Mono<Boolean> isMirrorAvailable(MirrorURI mirrorURI) {
		return this.availableMirrors.contains(mirrorURI);
	}
}
