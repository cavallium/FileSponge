package org.warp.filesponge;

import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

public class ThreadSafety {

	@Test
	public void threadSafety() {
		Scheduler schedulerSingle = Schedulers.newSingle("treadSafeScheduler");
		Scheduler schedulerParallel = Schedulers.newParallel("threadUnsafeScheduler", 20);

		int iterations = 500;
		List<Integer> list = new LinkedList<>();

		var flux = Flux.range(0, iterations)
				.flatMap(s -> Mono.fromCallable(() -> {
					list.add(s);
					return s;
				}).then(Mono.fromCallable(() -> {
					list.add(1);
					return s;
				})).subscribeOn(schedulerSingle))
				.ignoreElements()
				.thenMany(Flux.defer(() -> Flux.fromStream(list::stream).subscribeOn(Schedulers.boundedElastic())))
				.subscribeOn(schedulerParallel);

		Integer[] checks = new Integer[iterations * 2];
		for (int i = 0; i < iterations; i++) {
			checks[i * 2] = i;
			checks[i * 2 + 1] = 1;
		}
		StepVerifier
				.create(flux)
				.expectSubscription()
				.expectNext(checks)
				.verifyComplete();
	}
}
