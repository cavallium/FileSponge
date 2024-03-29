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

package org.warp.filesponge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class FileSpongeUtils {

	private static final Logger logger = LogManager.getLogger(FileSponge.class);

	public static <T> Mono<T> firstWithValueMono(List<Mono<T>> monos) {
		return Mono.firstWithValue(monos).onErrorResume(FileSpongeUtils::ignoreFakeErrors);
	}

	public static <T> Flux<T> firstWithValueFlux(List<Flux<T>> monos) {
		return Flux.firstWithValue(monos).onErrorResume(FileSpongeUtils::ignoreFakeErrors);
	}

	private static <T> Mono<T> ignoreFakeErrors(Throwable ex) {
		if (ex instanceof NoSuchElementException && Exceptions.isMultiple(ex.getCause())) {
			var multiple = Exceptions.unwrapMultipleExcludingTracebacks(ex.getCause());
			for (Throwable throwable : multiple) {
				if (!(throwable instanceof NoSuchElementException)) {
					var differentErrors = multiple.stream()
							.filter(e -> !(e instanceof NoSuchElementException))
							.toArray(Throwable[]::new);
					return Mono.error(Exceptions.multiple(differentErrors));
				}
			}
			return Mono.empty();
		} else {
			return Mono.error(ex);
		}
	}

	public static Mono<Path> deleteFileAfter(Path path, Duration delay) {
		return Mono.fromCallable(() -> {
			Schedulers.boundedElastic().schedule(() -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException e) {
					logger.warn("Failed to delete file \"{}\"", path, e);
				}
			}, delay.toMillis(), TimeUnit.MILLISECONDS);
			return path;
		}).subscribeOn(Schedulers.boundedElastic());
	}
}
