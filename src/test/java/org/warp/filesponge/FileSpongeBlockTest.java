package org.warp.filesponge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import it.cavallium.buffer.Buf;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class FileSpongeBlockTest {
	private final URL url = mock(URL.class);
	private final long size = 4L * FileSponge.BLOCK_SIZE + 3;

	@Test
	void coldSeekRequestsOnlyMissingBlockAndNeverSequentialContent() {
		var sponge = new FileSponge();
		var requested = new AtomicInteger(-1);
		var source = source(requested, block(3, FileSponge.BLOCK_SIZE));
		sponge.registerSource(source).block();
		var result = sponge.requestContentBlock(url, 3).block();
		assertEquals(3, requested.get());
		assertEquals(3L * FileSponge.BLOCK_SIZE, result.getOffset());
	}

	@Test
	void partialCacheHitDoesNotContactSource() {
		var sponge = new FileSponge();
		var cache = mock(DiskCache.class);
		var expected = block(4, 3);
		when(cache.requestMetadata(url)).thenReturn(Mono.just(new Metadata(size)));
		when(cache.requestContentBlock(url, 4)).thenReturn(Mono.just(expected));
		sponge.registerCache(cache).block();
		assertSame(expected, sponge.requestContentBlock(url, 4).block());
		verify(cache, never()).requestContent(any());
		verify(cache, never()).writeHashSync(any(), anyLong());
	}

	@Test
	void missingBlockIsCachedAfterKnownMetadataWithoutPublishingHash() {
		var sponge = new FileSponge();
		var cache = mock(DiskCache.class);
		var expected = block(4, 3);
		when(cache.requestMetadata(url)).thenReturn(Mono.just(new Metadata(size)));
		when(cache.requestContentBlock(url, 4)).thenReturn(Mono.empty());
		when(cache.writeMetadata(eq(url), any(), eq(false))).thenReturn(Mono.empty());
		when(cache.writeContentBlock(url, expected, false)).thenReturn(Mono.empty());
		sponge.registerCache(cache).block();
		sponge.registerSource(source(new AtomicInteger(), expected)).block();
		assertSame(expected, sponge.requestContentBlock(url, 4).block());
		var order = inOrder(cache);
		order.verify(cache).writeMetadata(url, new Metadata(size), false);
		order.verify(cache).writeContentBlock(url, expected, false);
		verify(cache, never()).writeHashSync(any(), anyLong());
		verify(cache, never()).writeAliasSync(any(), any());
	}

	@Test
	void rejectsTruncatedAndIncorrectOffsetBlocks() {
		for (var invalid : new DataBlock[]{block(3, 2), block(2, FileSponge.BLOCK_SIZE)}) {
			var sponge = new FileSponge();
			sponge.registerSource(source(new AtomicInteger(), invalid)).block();
			StepVerifier.create(sponge.requestContentBlock(url, 3)).expectError(IllegalStateException.class).verify();
		}
	}

	@Test
	void rejectsNegativeBlockAndReturnsEmptyBeyondEnd() {
		var sponge = new FileSponge();
		var requested = new AtomicInteger(-1);
		sponge.registerSource(source(requested, block(4, 3))).block();
		StepVerifier.create(sponge.requestContentBlock(url, -1)).expectError(IllegalArgumentException.class).verify();
		StepVerifier.create(sponge.requestContentBlock(url, 5)).verifyComplete();
		assertEquals(-1, requested.get());
	}

	@Test
	void unknownSizeNeverDownloadsBlock() {
		var sponge = new FileSponge();
		var source = mock(URLsHandler.class);
		when(source.requestMetadata(url)).thenReturn(Mono.just(new Metadata(-1)));
		sponge.registerSource(source).block();
		StepVerifier.create(sponge.requestContentBlock(url, 3)).expectError(IllegalStateException.class).verify();
		verify(source, never()).requestContentBlock(any(), anyInt());
	}

	@Test
	void sourceFailureRemainsAnErrorAndUnsupportedSourceNeverFallsBackToStream() {
		var sponge = new FileSponge();
		var source = mock(URLsHandler.class);
		when(source.requestMetadata(url)).thenReturn(Mono.just(new Metadata(size)));
		when(source.requestContentBlock(url, 3)).thenReturn(Mono.error(new IllegalStateException("download failed")));
		sponge.registerSource(source).block();
		StepVerifier.create(sponge.requestContentBlock(url, 3))
				.expectErrorMessage("download failed").verify();
		verify(source, never()).requestContent(any());
	}

	@Test
	void cancelledBlockDoesNotWriteCache() {
		var sponge = new FileSponge();
		var cache = mock(DiskCache.class);
		when(cache.requestMetadata(url)).thenReturn(Mono.just(new Metadata(size)));
		when(cache.requestContentBlock(url, 3)).thenReturn(Mono.empty());
		var source = mock(URLsHandler.class);
		when(source.requestContentBlock(url, 3)).thenReturn(Mono.never());
		sponge.registerCache(cache).block();
		sponge.registerSource(source).block();
		StepVerifier.create(sponge.requestContentBlock(url, 3)).thenCancel().verify();
		verify(cache, never()).writeContentBlock(any(), any(), anyBoolean());
		verify(source, never()).requestContent(any());
	}

	@Test
	void unavailableProviderFallsBackToAnotherDirectProvider() {
		var sponge = new FileSponge();
		var calls = new AtomicInteger();
		var expected = block(4, 3);
		for (int i = 0; i < 2; i++) {
			var provider = mock(URLsHandler.class);
			when(provider.requestMetadata(url)).thenReturn(Mono.just(new Metadata(size)));
			when(provider.requestContentBlock(url, 4)).thenReturn(Mono.defer(() -> calls.getAndIncrement() == 0
					? Mono.error(new BlockReadUnavailableException("Range unsupported")) : Mono.just(expected)));
			sponge.registerSource(provider).block();
		}
		assertSame(expected, sponge.requestContentBlock(url, 4).block());
		assertEquals(2, calls.get());
	}

	@Test
	void allUnavailableProvidersFailRatherThanReportEndOfFile() {
		var sponge = new FileSponge();
		var provider = mock(URLsHandler.class);
		when(provider.requestMetadata(url)).thenReturn(Mono.just(new Metadata(size)));
		when(provider.requestContentBlock(url, 4)).thenReturn(Mono.empty());
		sponge.registerSource(provider).block();
		StepVerifier.create(sponge.requestContentBlock(url, 4)).expectError(BlockReadUnavailableException.class).verify();
		verify(provider, never()).requestContent(any());
	}

	@Test
	void malformedBlockNeverFallsBackToAnotherProvider() {
		var sponge = new FileSponge();
		var calls = new AtomicInteger();
		for (int i = 0; i < 2; i++) {
			var provider = mock(URLsHandler.class);
			when(provider.requestMetadata(url)).thenReturn(Mono.just(new Metadata(size)));
			when(provider.requestContentBlock(url, 4)).thenReturn(Mono.fromSupplier(() -> {
				calls.incrementAndGet();
				return block(4, 2);
			}));
			sponge.registerSource(provider).block();
		}
		StepVerifier.create(sponge.requestContentBlock(url, 4)).expectError(IllegalStateException.class).verify();
		assertEquals(1, calls.get());
	}

	private URLsHandler source(AtomicInteger requested, DataBlock result) {
		return new URLsHandler() {
			@Override public Flux<DataBlock> requestContent(URL ignored) {
				return Flux.error(new AssertionError("Sequential download must not be used"));
			}
			@Override public Mono<DataBlock> requestContentBlock(URL ignored, int blockId) {
				return Mono.fromSupplier(() -> { requested.set(blockId); return result; });
			}
			@Override public Mono<Metadata> requestMetadata(URL ignored) {
				return Mono.just(new Metadata(size));
			}
		};
	}

	private static DataBlock block(int id, int length) {
		return DataBlock.of((long) id * FileSponge.BLOCK_SIZE, length, Buf.wrap(new byte[length]));
	}
}
