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

public interface URLsDiskHandler extends URLsHandler {

	Mono<DiskMetadata> requestDiskMetadata(URL url);

	default URLDiskHandler asURLDiskHandler(URL url) {
		return new URLDiskHandler() {
			@Override
			public Mono<DiskMetadata> requestDiskMetadata() {
				return URLsDiskHandler.this.requestDiskMetadata(url);
			}

			@Override
			public Flux<DataBlock> requestContent() {
				return URLsDiskHandler.this.requestContent(url);
			}

			@Override
			public Mono<Metadata> requestMetadata() {
				return URLsDiskHandler.this.requestMetadata(url);
			}
		};
	}

}
