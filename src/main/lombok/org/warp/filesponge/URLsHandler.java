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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public interface URLsHandler {

	Flux<DataBlock> requestContent(URL url);

	Mono<Metadata> requestMetadata(URL url);

	default Mono<Tuple2<Metadata, Flux<DataBlock>>> request(URL url) {
		return requestMetadata(url).map(metadata -> Tuples.of(metadata, requestContent(url)));
	}

	default URLHandler asURLHandler(URL url) {
		return new URLHandler() {
			@Override
			public Flux<DataBlock> requestContent() {
				return URLsHandler.this.requestContent(url);
			}

			@Override
			public Mono<Metadata> requestMetadata() {
				return URLsHandler.this.requestMetadata(url);
			}
		};
	}

}
