package org.warp.filesponge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import it.cavallium.buffer.Buf;
import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class FileSpongeStreamTest {
    @Test
    void streamInitializesMetadataAndWaitsForEachBlockWrite() {
        verifyStreamWrites(false);
    }

    @Test
    void existingMetadataStillRequiresOrderedBlockWrites() {
        verifyStreamWrites(true);
    }

    private void verifyStreamWrites(boolean metadataExists) {
        var sponge = new FileSponge();
        var url = mock(URL.class);
        var cache = mock(DiskCache.class);
        var source = mock(URLsHandler.class);
        var first = DataBlock.of(0, FileSponge.BLOCK_SIZE, Buf.wrap(new byte[FileSponge.BLOCK_SIZE]));
        var last = DataBlock.of(FileSponge.BLOCK_SIZE, 3, Buf.wrap(new byte[3]));
        var writes = new ArrayList<Integer>();
        var metadataWritten = new java.util.concurrent.atomic.AtomicBoolean(metadataExists);
        var firstWrite = Sinks.<Void>empty();
        when(cache.requestMetadata(url)).thenReturn(metadataExists
                ? Mono.just(new Metadata(FileSponge.BLOCK_SIZE + 3L)) : Mono.empty());
        when(cache.requestContent(url)).thenReturn(Flux.empty());
        when(cache.writeMetadata(url, new Metadata(-1), false)).thenReturn(Mono.fromRunnable(() -> metadataWritten.set(true)));
        when(cache.writeContentBlock(eq(url), any(), eq(false))).thenAnswer(invocation -> {
            DataBlock block = invocation.getArgument(1);
            return Mono.defer(() -> {
                if (!metadataWritten.get()) return Mono.error(new IllegalStateException("Missing metadata"));
                writes.add(block.getId());
                return block.getId() == 0 ? firstWrite.asMono() : Mono.empty();
            });
        });
        when(source.requestMetadata(url)).thenReturn(Mono.just(new Metadata(-1)));
        when(source.requestContent(url)).thenReturn(Flux.just(first, last));
        sponge.registerCache(cache).block();
        sponge.registerSource(source).block();
        StepVerifier.create(sponge.requestContent(url))
                .then(() -> assertEquals(java.util.List.of(0), writes))
                .then(() -> firstWrite.tryEmitEmpty())
                .expectNext(first, last)
                .expectComplete().verify(Duration.ofSeconds(5));
        assertEquals(java.util.List.of(0, 1), writes);
    }
}
