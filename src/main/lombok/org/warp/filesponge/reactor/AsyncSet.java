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

package org.warp.filesponge.reactor;

import java.util.Set;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Asynchronous set
 * @param <T> value type
 */
public interface AsyncSet<T> {

	/**
	 * Clear the set
	 *
	 * @return void
	 */
	Mono<Void> clear();

	/**
	 * Add element to the set
	 *
	 * @param value value to add
	 * @return true if added, false if it's already present. Can't be empty.
	 */
	Mono<Boolean> add(T value);

	/**
	 * Remove element from the set
	 *
	 * @param value value to remove
	 * @return true if removed, false if it's not present. Can't be empty.
	 */
	Mono<Boolean> remove(T value);

	/**
	 * Find if element is present in the set
	 *
	 * @param value value to find
	 * @return true if found, false if not. Can't be empty.
	 */
	Mono<Boolean> contains(T value);

	/**
	 * Get the set size
	 *
	 * @return set size, from 0 to {@value Integer#MAX_VALUE}. Can't be empty.
	 */
	Mono<Integer> size();

	/**
	 * Get all values
	 * @return values, in a flux. Can be empty.
	 */
	Flux<T> toFlux();

	/**
	 * Get all values
	 * @return values, in a set. Can't be empty.
	 */
	Mono<Set<T>> toSet();
}
