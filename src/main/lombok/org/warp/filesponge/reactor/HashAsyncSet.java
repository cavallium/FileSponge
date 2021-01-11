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

import java.util.HashSet;
import java.util.Set;
import lombok.EqualsAndHashCode;
import org.warp.commonutils.concurrency.atomicity.NotAtomic;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@NotAtomic
@EqualsAndHashCode
public class HashAsyncSet<T> implements AsyncSet<T> {

	private final HashSet<T> set;

	public HashAsyncSet() {
		this.set = new HashSet<>();
	}

	public HashAsyncSet(HashSet<T> set) {
		this.set = set;
	}

	@Override
	public Mono<Void> clear() {
		return Mono.fromCallable(() -> {
			set.clear();
			return null;
		});
	}

	@Override
	public Mono<Boolean> add(T value) {
		return Mono.fromCallable(() -> set.add(value));
	}

	@Override
	public Mono<Boolean> remove(T value) {
		return Mono.fromCallable(() -> set.remove(value));
	}

	@Override
	public Mono<Boolean> contains(T value) {
		return Mono.fromCallable(() -> set.contains(value));
	}

	@Override
	public Mono<Integer> size() {
		return Mono.fromCallable(set::size);
	}

	@Override
	public Flux<T> toFlux() {
		return Flux.fromStream(set::stream);
	}

	@Override
	public Mono<Set<T>> toSet() {
		return Mono.fromCallable(() -> Set.copyOf(set));
	}

	@Override
	public String toString() {
		return set.toString();
	}

	public SynchronizedHashAsyncSet<T> synchronize() {
		return new SynchronizedHashAsyncSet<>(this);
	}
}
