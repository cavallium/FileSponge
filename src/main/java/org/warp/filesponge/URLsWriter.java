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

import reactor.core.publisher.Mono;

public interface URLsWriter {

	/**
	 * @param force true to force writing onto a cache, ignoring the shouldCache predicate
	 */
	Mono<Void> writeMetadata(URL url, Metadata metadata, boolean force);

	/**
	 * @param force true to force writing onto a cache, ignoring the shouldCache predicate
	 */
	Mono<Void> writeContentBlock(URL url, DataBlock dataBlock, boolean force);

	default URLWriter getUrlWriter(URL url) {
		return new URLWriter() {

			/**
			 * @param force true to force writing onto a cache, ignoring the shouldCache predicate
			 */
			@Override
			public Mono<Void> writeMetadata(Metadata metadata, boolean force) {
				return URLsWriter.this.writeMetadata(url, metadata, force);
			}

			/**
			 * @param force true to force writing onto a cache, ignoring the shouldCache predicate
			 */
			@Override
			public Mono<Void> writeContentBlock(DataBlock dataBlock, boolean force) {
				return URLsWriter.this.writeContentBlock(url, dataBlock, force);
			}
		};
	}

}
