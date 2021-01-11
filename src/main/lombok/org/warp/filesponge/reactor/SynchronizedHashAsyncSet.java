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
import lombok.EqualsAndHashCode;
import org.warp.commonutils.concurrency.atomicity.NotAtomic;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@EqualsAndHashCode
@NotAtomic
public class SynchronizedHashAsyncSet<T> implements AsyncSet<T> {

	private transient final Scheduler scheduler = Schedulers.single();
	private final HashAsyncSet<T> set;

	public SynchronizedHashAsyncSet() {
		this.set = new HashAsyncSet<>();
	}

	public SynchronizedHashAsyncSet(HashAsyncSet<T> set) {
		this.set = set;
	}

	@Override
	public Mono<Void> clear() {
		return set.clear().subscribeOn(scheduler);
	}

	@Override
	public Mono<Boolean> add(T value) {
		return set.add(value).subscribeOn(scheduler);
	}

	@Override
	public Mono<Boolean> remove(T value) {
		return set.remove(value).subscribeOn(scheduler);
	}

	@Override
	public Mono<Boolean> contains(T value) {
		return set.contains(value).subscribeOn(scheduler);
	}

	@Override
	public Mono<Integer> size() {
		return set.size().subscribeOn(scheduler);
	}

	@Override
	public Flux<T> toFlux() {
		return set.toFlux().subscribeOn(scheduler);
	}

	@Override
	public Mono<Set<T>> toSet() {
		return set.toSet().subscribeOn(scheduler);
	}

	@Override
	public String toString() {
		return set.toString();
	}
}
