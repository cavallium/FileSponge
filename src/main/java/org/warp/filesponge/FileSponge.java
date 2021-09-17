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

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.warp.commonutils.log.Logger;
import org.warp.commonutils.log.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class FileSponge implements URLsHandler {

	private static final Logger logger = LoggerFactory.getLogger(FileSponge.class);

	public static final int BLOCK_SIZE = 1024 * 1024; // 1 MiB

	private final Object structuresLock = new Object();
	private volatile ObjectOpenHashSet<URLsHandler> urlsHandlers = ObjectOpenHashSet.of();
	private volatile ObjectOpenHashSet<URLsDiskHandler> cacheAccess = ObjectOpenHashSet.of();
	private volatile ObjectOpenHashSet<URLsWriter> cacheWrite = ObjectOpenHashSet.of();

	public FileSponge() {

	}

	public Mono<Void> registerSource(URLsHandler urLsHandler) {
		return Mono
				.<Void>fromRunnable(() -> {
					synchronized (structuresLock) {
						var clone = urlsHandlers.clone();
						clone.add(urLsHandler);
						this.urlsHandlers = clone;
					}
				})
				.subscribeOn(Schedulers.boundedElastic());
	}

	public <T extends URLsDiskHandler & URLsWriter> Mono<Void> registerCache(T urlsCache) {
		return Mono
				.<Void>fromRunnable(() -> {
					synchronized (structuresLock) {
						var cacheAccessClone = cacheAccess.clone();
						cacheAccessClone.add(urlsCache);
						this.cacheAccess = cacheAccessClone;

						var cacheWriteClone = cacheWrite.clone();
						cacheWriteClone.add(urlsCache);
						this.cacheWrite = cacheWriteClone;
					}
				})
				.subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Flux<DataBlock> requestContent(URL url) {
		AtomicBoolean alreadyPrintedDebug = new AtomicBoolean(false);
		return Mono
				.fromCallable(() -> {
					List<Flux<DataBlock>> contentRequests = new ArrayList<>(cacheAccess.size());
					for (URLsDiskHandler urlsHandler : cacheAccess) {
						contentRequests.add(urlsHandler.requestContent(url));
					}
					return contentRequests;
				})
				.flatMapMany(FileSpongeUtils::firstWithValueFlux)
				.doOnNext(dataBlock -> {
					if (alreadyPrintedDebug.compareAndSet(false, true)) {
						logger.debug("File \"{}\" content has been found in the cache", url);
					}
				})
				.switchIfEmpty(Mono
						.fromCallable(() -> {
							logger.debug("Downloading file \"{}\" content", url);
							List<Flux<DataBlock>> contentRequestsAndCaching = new ArrayList<>(urlsHandlers.size());
							for (URLsHandler urlsHandler : urlsHandlers) {
								contentRequestsAndCaching.add(urlsHandler
										.requestContent(url)
										.flatMapSequential(dataBlock -> {
											List<Mono<Void>> cacheWriteActions = new ArrayList<>(cacheWrite.size());
											for (URLsWriter urlsWriter : cacheWrite) {
												cacheWriteActions.add(urlsWriter.writeContentBlock(url, dataBlock));
											}
											return Mono.when(cacheWriteActions).thenReturn(dataBlock);
										})
								);
							}
							return contentRequestsAndCaching;
						})
						.flatMapMany(FileSpongeUtils::firstWithValueFlux)
						.doOnComplete(() -> logger.debug("Downloaded file \"{}\" content", url))
				)
				.distinct(DataBlock::getId)

				.doOnDiscard(DataBlock.class, DataBlock::close);
	}

	@Override
	public Mono<Metadata> requestMetadata(URL url) {
		return Mono
				.fromCallable(() -> {
					List<Mono<Metadata>> metadataRequests = new ArrayList<>(cacheAccess.size());
					for (URLsDiskHandler urlsHandler : cacheAccess) {
						metadataRequests.add(urlsHandler.requestMetadata(url));
					}
					return metadataRequests;
				})
				.flatMap(FileSpongeUtils::firstWithValueMono)
				.doOnSuccess(metadata -> {
					if (metadata != null) {
						logger.debug("File \"{}\" metadata has been found in the cache", url);
					}
				})
				.switchIfEmpty(Mono
						.fromCallable(() -> {
							logger.debug("Downloading file \"{}\" metadata", url);
							List<Mono<Metadata>> metadataRequestsAndCaching = new ArrayList<>(urlsHandlers.size());
							for (URLsHandler urlsHandler : urlsHandlers) {
								metadataRequestsAndCaching.add(urlsHandler
										.requestMetadata(url)
										.flatMap(meta -> {
											List<Mono<Void>> cacheWriteActions = new ArrayList<>(cacheWrite.size());
											for (URLsWriter urlsWriter : cacheWrite) {
												cacheWriteActions.add(urlsWriter.writeMetadata(url, meta));
											}
											return Mono.when(cacheWriteActions).thenReturn(meta);
										})
								);
							}
							return metadataRequestsAndCaching;
						})
						.flatMap(FileSpongeUtils::firstWithValueMono)
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
