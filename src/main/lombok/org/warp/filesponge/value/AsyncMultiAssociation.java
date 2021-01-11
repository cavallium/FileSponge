/*
 *     FileSponge
 *     Copyright (C) 2021 Andrea Cavalli
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

package org.warp.filesponge.value;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AsyncMultiAssociation<T, U> {

	Mono<Boolean> link(T var1, U var2);

	Mono<Boolean> unlink(T var1, U var2);

	Flux<U> unlink(T var1);

	Flux<T> unlinkFromSource(U var1);

	default Mono<Boolean> hasAnyLink(T src) {
		return this.getLinks(src).hasElements();
	}

	default Mono<Boolean> hasAnyLinkSource(U dest) {
		return this.getLinkSources(dest).hasElements();
	}

	Mono<Boolean> hasLink(T var1, U var2);

	Flux<U> getLinks(T var1);

	Flux<T> getLinkSources(U var1);

	Mono<Void> clear();

	Mono<Integer> size();

	Flux<T> getSources();

	Flux<U> getDestinations();
}
