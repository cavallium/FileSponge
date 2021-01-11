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

import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.warp.filesponge.value.MirrorURI;
import reactor.core.publisher.Mono;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class MirrorAvailabilityManager {

	private static final Object NO_VALUE = new Object();
	/**
	 * This is a set. the value is not important
	 */
	private final ConcurrentHashMap<MirrorURI, Object> availableMirrors = new ConcurrentHashMap<>();

	public Mono<Void> setAllMirrorsAsUnavailable() {
		return Mono.fromCallable(() -> {
			availableMirrors.clear();
			return null;
		});
	}

	public Mono<Void> setMirrorAvailability(MirrorURI mirrorURI, boolean available) {
		return Mono.fromCallable(() -> {
			if (available) {
				availableMirrors.put(mirrorURI, NO_VALUE);
			} else {
				availableMirrors.remove(mirrorURI);
			}
			return null;
		});
	}

	public Mono<Boolean> isMirrorAvailable(MirrorURI mirrorURI) {
		return Mono.fromCallable(() -> this.availableMirrors.contains(mirrorURI));
	}
}
