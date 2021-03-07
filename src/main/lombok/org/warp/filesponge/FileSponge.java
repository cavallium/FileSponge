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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class FileSponge implements URLsHandler {

	public static final int BLOCK_SIZE = 8 * 1024 * 1024; // 8 MiB
	public static final int MAX_BLOCKS = 256; // 2 GiB

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
		return Flux
				.fromIterable(cacheAccess)
				.flatMap(urlsHandler -> urlsHandler.requestContent(url))
				.switchIfEmpty(Flux
						.fromIterable(urlsHandlers)
						.flatMap(urlsHandler -> urlsHandler
								.requestContent(url)
								.flatMapSequential(dataBlock -> Flux
										.fromIterable(cacheWrite)
										.flatMapSequential(cw -> cw.writeContentBlock(url, dataBlock))
										.then(Mono.just(dataBlock))
								)
						)
				)
				.distinct(DataBlock::getId);
	}

	@Override
	public Mono<Metadata> requestMetadata(URL url) {
		return Mono.from(Flux
				.fromIterable(cacheAccess)
				.flatMap(urlsHandler -> urlsHandler.requestMetadata(url))
				.switchIfEmpty(Flux
						.fromIterable(urlsHandlers)
						.flatMap(urlsHandler -> urlsHandler
								.requestMetadata(url)
								.flatMap(dataBlock -> Flux
										.fromIterable(cacheWrite)
										.flatMapSequential(cw -> cw.writeMetadata(url, dataBlock))
										.then(Mono.just(dataBlock))
								)
						)
				));
		}
}
