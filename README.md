# FileSponge

FileSponge is a reactive Java library designed to proxy file requests to multiple consistent mirrors of a single immutable remote source. It ensures high availability and performance by caching the first successful response into a local database.

Originally created to retrieve specific files from multiple sources (e.g., Telegram bots) without needing to know which source has the file, FileSponge has evolved into a robust general-purpose file fetching and caching solution.

## Features

*   **Reactive Architecture**: Built on top of [Project Reactor](https://projectreactor.io/), allowing for non-blocking, asynchronous file handling.
*   **Smart Caching**: Automatically stores downloaded content in a local database (via `dbengine`), ensuring subsequent requests are served instantly from the disk.
*   **Multiple Sources**: Supports registering multiple `URLsHandler` sources. The library will attempt to fetch content from them, seamlessly handling failures or missing files on specific mirrors.
*   **Block-Based Storage**: Handles files in chunks (`DataBlock`), suitable for streaming large files without loading them entirely into memory.
*   **Extensible**: Easy to implement custom URL types and data sources.

## Requirements

*   Java 25 or higher
*   Maven

## Dependencies

FileSponge relies on the following key libraries:
*   `it.cavallium:dbengine`: For local database storage.
*   `io.projectreactor:reactor-core`: For reactive programming models.

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.warp.filesponge</groupId>
    <artifactId>FileSponge</artifactId>
    <version>0.3.2</version>
</dependency>
```

## Usage

### 1. Define your URL

Implement the `URL` interface to represent the resources you want to fetch.

```java
import org.warp.filesponge.URL;
import org.warp.filesponge.URLSerializer;
import org.warp.filesponge.URLStringSerializer;
import org.jetbrains.annotations.NotNull;

public record MyResourceURL(String id) implements URL {
    @Override
    public URLSerializer<? extends URL> getSerializer() {
        return new URLStringSerializer<>() {
            @Override
            public @NotNull String serialize(@NotNull MyResourceURL url) {
                return url.id();
            }
        };
    }

    @Override
    public String toString() {
        return "MyResourceURL[" + id + "]";
    }
}
```

### 2. Implement a Source Handler

Create a `URLsHandler` that knows how to fetch data from your upstream source (e.g., HTTP server, S3, or another service).

```java
import org.warp.filesponge.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import it.cavallium.buffer.Buf;

public class MySourceHandler implements URLsHandler {
    @Override
    public Flux<DataBlock> requestContent(URL url) {
        if (url instanceof MyResourceURL resourceUrl) {
            // Logic to download file as a stream of DataBlocks
            // This is a simplified example. In reality, you'd fetch bytes and chunk them.
            return Flux.create(sink -> {
                // ... download logic ...
                // sink.next(DataBlock.of(offset, length, Buf.wrap(bytes)));
                sink.complete();
            });
        }
        return Flux.empty();
    }

    @Override
    public Mono<Metadata> requestMetadata(URL url) {
        // Return file metadata (size, content type, hash, etc.)
        return Mono.just(new Metadata(1024, "text/plain", "sha256-hash", null));
    }
}
```

### 3. Setup FileSponge and Cache

Initialize `FileSponge`, register your source, and set up the disk cache.

```java
import org.warp.filesponge.FileSponge;
import org.warp.filesponge.DiskCache;
import it.cavallium.dbengine.database.LLDatabaseConnection;
import it.cavallium.dbengine.rpc.current.data.DatabaseOptions;

public class FileSpongeExample {
    public static void main(String[] args) {
        // 1. Initialize FileSponge
        FileSponge fileSponge = new FileSponge();

        // 2. Register Source
        fileSponge.registerSource(new MySourceHandler()).block();

        // 3. Setup and Register Disk Cache
        // Note: You need a configured LLDatabaseConnection from 'dbengine'
        LLDatabaseConnection dbConnection = ...; 
        DatabaseOptions dbOptions = ...;
        
        DiskCache diskCache = DiskCache.open(
            dbConnection, 
            "my_file_cache", 
            dbOptions, 
            url -> true // Predicate to decide what to cache
        );
        
        fileSponge.registerCache(diskCache).block();

        // 4. Request File Content
        MyResourceURL url = new MyResourceURL("file-123");
        
        fileSponge.requestContent(url)
            .doOnNext(dataBlock -> {
                System.out.println("Received block: " + dataBlock.getLength() + " bytes");
                // Process data (e.g., write to output stream)
            })
            .blockLast(); // Wait for completion
    }
}
```

## How It Works

1.  **Request**: You call `fileSponge.requestContent(url)`.
2.  **Cache Check**: FileSponge checks registered `DiskCache` instances.
3.  **Hit**: If found, data is streamed from the disk.
4.  **Miss**: If not found, FileSponge queries registered `URLsHandler` sources.
5.  **Fetch & Store**: The first source to respond streams the data. FileSponge simultaneously passes this data to you and writes it to the `DiskCache`.
6.  **Next Request**: Future requests for the same URL will be served from the cache.