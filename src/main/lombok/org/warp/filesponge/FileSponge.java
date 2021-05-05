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

import it.cavallium.dbengine.database.disk.LLLocalDictionary.ReleasableSlice;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class FileSponge implements URLsHandler {

	private static final Logger logger = LoggerFactory.getLogger(FileSponge.class);

	public static final int BLOCK_SIZE = 1024 * 1024; // 1 MiB

	private final Set<URLsHandler> urlsHandlers = new ConcurrentHashMap<URLsHandler, Object>().keySet(new Object());

	private final Set<URLsDiskHandler> cacheAccess = new ConcurrentHashMap<URLsDiskHandler, Object>().keySet(new Object());
	private final Set<URLsWriter> cacheWrite = new ConcurrentHashMap<URLsWriter, Object>().keySet(new Object());

	public FileSponge() {

	}

	public Mono<Void> registerSource(URLsHandler urLsHandler) {
		return Mono.fromRunnable(() -> urlsHandlers.add(urLsHandler));
	}

	public <T extends URLsDiskHandler & URLsWriter> Mono<Void> registerCache(T urlsCache) {
		return Mono.fromRunnable(() -> {
			cacheAccess.add(urlsCache);
			cacheWrite.add(urlsCache);
		});
	}

	@Override
	public Flux<DataBlock> requestContent(URL url) {
		AtomicBoolean alreadyPrintedDebug = new AtomicBoolean(false);
		return Flux
				.fromIterable(cacheAccess)
				.map(urlsHandler -> urlsHandler.requestContent(url))
				.collectList()
				.doOnDiscard(DataBlock.class, DataBlock::release)
				.flatMapMany(monos -> FileSpongeUtils.firstWithValueFlux(monos))
				.doOnNext(dataBlock -> {
					if (alreadyPrintedDebug.compareAndSet(false, true)) {
						logger.debug("File \"{}\" content has been found in the cache", url);
					}
				})
				.switchIfEmpty(Flux
						.fromIterable(urlsHandlers)
						.doOnSubscribe(s -> logger.debug("Downloading file \"{}\" content", url))
						.map(urlsHandler -> urlsHandler
								.requestContent(url)
								.flatMapSequential(dataBlock -> Flux
										.fromIterable(cacheWrite)
										.flatMapSequential(cw -> cw.writeContentBlock(url, dataBlock))
										.then(Mono.just(dataBlock))
								)
						)
						.collectList()
						.doOnDiscard(Flux.class, f -> {
							//noinspection unchecked
							Flux<DataBlock> flux = (Flux<DataBlock>) f;
							flux.doOnNext(DataBlock::release).subscribeOn(Schedulers.single()).subscribe();
						})
						.flatMapMany(monos -> FileSpongeUtils.firstWithValueFlux(monos))
						.doOnComplete(() -> logger.debug("Downloaded file \"{}\" content", url))
				)
				.distinct(DataBlock::getId)
				.doOnDiscard(DataBlock.class, DataBlock::release);
	}

	@Override
	public Mono<Metadata> requestMetadata(URL url) {
		return Flux
				.fromIterable(cacheAccess)
				.map(urlsHandler -> urlsHandler.requestMetadata(url))
				.collectList()
				.flatMap(monos -> FileSpongeUtils.firstWithValueMono(monos))
				.doOnSuccess(metadata -> {
					if (metadata != null) {
						logger.debug("File \"{}\" metadata has been found in the cache", url);
					}
				})
				.switchIfEmpty(Flux
						.fromIterable(urlsHandlers)
						.doOnSubscribe(s -> logger.debug("Downloading file \"{}\" metadata", url))
						.map(urlsHandler -> urlsHandler
								.requestMetadata(url)
								.flatMap(dataBlock -> Flux
										.fromIterable(cacheWrite)
										.flatMapSequential(cw -> cw.writeMetadata(url, dataBlock))
										.then(Mono.just(dataBlock))
								)
						)
						.collectList()
						.flatMap(monos -> FileSpongeUtils.firstWithValueMono(monos))
						.doOnSuccess(s -> {
							if (s != null) {
								logger.debug("Downloaded file \"{}\" metadata", url);
							} else {
								logger.debug("File \"{}\" metadata has not been found anywhere", url);
							}
						})
				);
	}
}
