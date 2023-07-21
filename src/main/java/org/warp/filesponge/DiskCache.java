/*
 *     FileSponge
 *     Copyright (C) 2023 Andrea Cavalli
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

import it.cavallium.dbengine.client.IBackuppable;
import it.cavallium.dbengine.database.ColumnUtils;
import it.cavallium.dbengine.database.LLDatabaseConnection;
import it.cavallium.dbengine.database.LLDictionary;
import it.cavallium.dbengine.database.LLKeyValueDatabase;
import it.cavallium.dbengine.database.SafeCloseable;
import it.cavallium.dbengine.database.UpdateMode;
import it.cavallium.dbengine.rpc.current.data.DatabaseOptions;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import reactor.util.function.Tuple2;

public interface DiskCache extends URLsDiskHandler, URLsWriter, SafeCloseable {

	void writeMetadataSync(URL url, Metadata metadata);

	void writeContentBlockSync(URL url, DataBlock dataBlock);

	Stream<DataBlock> requestContentSync(URL url);

	DiskMetadata requestDiskMetadataSync(URL url);

	Metadata requestMetadataSync(URL url);

	Tuple2<Metadata, Stream<DataBlock>> requestSync(URL url);

	static DiskCache open(LLDatabaseConnection databaseConnection,
			String dbName,
			DatabaseOptions databaseOptions,
			Predicate<URL> shouldCache) {
		var db = databaseConnection.getDatabase(dbName,
				List.of(ColumnUtils.dictionary("file-content"), ColumnUtils.dictionary("file-metadata")),
				databaseOptions
		);
		var dict1 = db.getDictionary("file-content", UpdateMode.ALLOW);
		var dict2 = db.getDictionary("file-metadata", UpdateMode.ALLOW);
		return new DiskCacheImpl(db, dict1, dict2, shouldCache);
	}

	static DiskCache openCustom(LLDictionary fileContent,
			LLDictionary fileMetadata,
			Predicate<URL> shouldCache) {
		return new DiskCacheImpl(null, fileContent, fileMetadata, shouldCache);
	}
}
