package org.warp.filesponge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import it.cavallium.buffer.Buf;
import it.cavallium.buffer.BufDataOutput;
import it.cavallium.dbengine.database.LLDictionary;
import it.cavallium.dbengine.database.LLDictionaryResultType;
import it.cavallium.dbengine.database.UpdateReturnMode;
import it.cavallium.dbengine.database.serialization.DatabaseDataInput;
import it.cavallium.dbengine.database.serialization.SerializationFunction;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public class DiskCacheImplTest {

    private LLDictionary fileContent;
    private LLDictionary fileMetadata;
    private LLDictionary fileAliases;
    private LLDictionary fileHashes;
    private Predicate<URL> shouldCache;
    private DiskCacheImpl diskCache;

    @BeforeEach
    void setUp() {
        fileContent = mock(LLDictionary.class);
        fileMetadata = mock(LLDictionary.class);
        fileAliases = mock(LLDictionary.class);
        fileHashes = mock(LLDictionary.class);
        shouldCache = url -> true;
        diskCache = new DiskCacheImpl(null, fileContent, fileMetadata, fileAliases, fileHashes, shouldCache);
    }

    @Test
    void readsOneBlockFromPartialCacheThroughAliasWithoutPrefixReads() {
        URL original = mockUrl("original-block");
        URL alias = mockUrl("alias-block");
        Buf key = serialize(original);
        when(fileAliases.get(null, serialize(alias))).thenReturn(key);
        when(fileMetadata.get(null, key)).thenReturn(serializeMetadata(new DiskMetadata(
                3L * FileSponge.BLOCK_SIZE + 7, new BooleanArrayList(new boolean[]{false, false, false, true}))));
        var out = BufDataOutput.create(key.size() + Integer.BYTES);
        out.writeBytes(key);
        out.writeInt(3);
        var blockKey = out.asList();
        when(fileContent.get(null, blockKey)).thenReturn(Buf.wrap(new byte[7]));

        var block = diskCache.requestContentBlockSync(alias, 3);
        assertEquals(3L * FileSponge.BLOCK_SIZE, block.getOffset());
        assertEquals(7, block.getLength());
        verify(fileContent).get(null, blockKey);
        verifyNoMoreInteractions(fileContent);
        assertNull(diskCache.requestContentBlockSync(alias, 2));
        assertNull(diskCache.requestContentBlockSync(alias, 4));
        verifyNoMoreInteractions(fileContent);
    }

    @Test
    void rejectsTruncatedCachedBlock() {
        URL url = mockUrl("truncated-block");
        Buf key = serialize(url);
        when(fileMetadata.get(null, key)).thenReturn(serializeMetadata(new DiskMetadata(
                7, new BooleanArrayList(new boolean[]{true}))));
        when(fileContent.get(eq(null), any())).thenReturn(Buf.wrap(new byte[6]));
        assertThrows(IllegalStateException.class, () -> diskCache.requestContentBlockSync(url, 0));
    }

    @Test
    void sequentialCachedReadRejectsShortIntermediateFinalAndMissingBlocks() {
        URL url = mockUrl("corrupt-stream");
        Buf key = serialize(url);
        when(fileMetadata.get(null, key)).thenReturn(serializeMetadata(new DiskMetadata(
                FileSponge.BLOCK_SIZE + 7L, new BooleanArrayList(new boolean[]{true, true}))));
        for (var invalid : List.of(Buf.wrap(new byte[6]), Buf.wrap(new byte[FileSponge.BLOCK_SIZE - 1]))) {
            when(fileContent.get(null, getBlockKey(key, 0))).thenReturn(invalid);
            when(fileContent.get(null, getBlockKey(key, 1))).thenReturn(Buf.wrap(new byte[7]));
            try (var stream = diskCache.requestContentSync(url)) {
                assertThrows(IllegalStateException.class, stream::toList);
            }
        }
        when(fileContent.get(null, getBlockKey(key, 0))).thenReturn(Buf.wrap(new byte[FileSponge.BLOCK_SIZE]));
        when(fileContent.get(null, getBlockKey(key, 1))).thenReturn(Buf.wrap(new byte[6]));
        try (var stream = diskCache.requestContentSync(url)) {
            assertThrows(IllegalStateException.class, stream::toList);
        }
        when(fileContent.get(null, getBlockKey(key, 1))).thenReturn(null);
        try (var stream = diskCache.requestContentSync(url)) {
            assertThrows(IllegalStateException.class, stream::toList);
        }
    }

    @Test
    void unknownSizeCacheNeverClaimsACompleteFile() {
        URL url = mockUrl("unfinished");
        when(fileMetadata.get(null, serialize(url))).thenReturn(serializeMetadata(
                new DiskMetadata(-1, new BooleanArrayList(new boolean[]{true}))));
        try (var stream = diskCache.requestContentSync(url)) {
            assertEquals(0, stream.count());
        }
        verifyNoInteractions(fileContent);
    }

    @Test
    void invalidKnownSizeWritesAndOverwritesLeaveContentAndMetadataUntouched() {
        var content = backing(fileContent);
        var metadata = backing(fileMetadata);
        URL url = mockUrl("validated-block");
        Buf key = serialize(url);
        var invalid = java.util.Arrays.asList(
                DataBlock.of(0, 3, Buf.wrap(new byte[3])),
                DataBlock.of(0, 5, Buf.wrap(new byte[5])),
                DataBlock.of(1, 4, Buf.wrap(new byte[4])),
                DataBlock.of(-1, 4, Buf.wrap(new byte[4])),
                DataBlock.of(FileSponge.BLOCK_SIZE, 4, Buf.wrap(new byte[4])),
                DataBlock.of(0, 4, Buf.wrap(new byte[3])),
                DataBlock.of(0, 4, null),
                DataBlock.of(Long.MAX_VALUE, 4, Buf.wrap(new byte[4])), null);
        for (boolean overwrite : List.of(false, true)) {
            var originalMetadata = serializeMetadata(new DiskMetadata(4, new BooleanArrayList(new boolean[]{overwrite})));
            metadata.put(key, originalMetadata);
            content.clear();
            if (overwrite) content.put(getBlockKey(key, 0), Buf.wrap(new byte[]{1, 2, 3, 4}));
            var originalContent = new HashMap<>(content);
            clearInvocations(fileContent, fileMetadata);
            for (var block : invalid) {
                assertThrows(IllegalStateException.class, () -> diskCache.writeContentBlockSync(url, block, true));
                assertEquals(originalContent, content);
                assertEquals(originalMetadata, metadata.get(key));
            }
            verify(fileContent, never()).put(any(), any(), any());
            verify(fileMetadata, never()).update(any(), any(), any());
        }
    }

    @Test
    void validKnownSizeFullAndFinalBlocksBecomeReadableAndComplete() {
        var content = backing(fileContent);
        var metadata = backing(fileMetadata);
        URL url = mockUrl("valid-tail");
        Buf key = serialize(url);
        metadata.put(key, serializeMetadata(new DiskMetadata(FileSponge.BLOCK_SIZE + 3L,
                new BooleanArrayList(new boolean[]{false, false}))));
        diskCache.writeContentBlockSync(url, DataBlock.of(0, FileSponge.BLOCK_SIZE,
                Buf.wrap(new byte[FileSponge.BLOCK_SIZE])), true);
        assertFalse(diskCache.requestDiskMetadataSync(url).isDownloadedFully());
        var tail = DataBlock.of(FileSponge.BLOCK_SIZE, 3, Buf.wrap(new byte[]{1, 2, 3}));
        diskCache.writeContentBlockSync(url, tail, true);
        assertTrue(diskCache.requestDiskMetadataSync(url).isDownloadedFully());
        assertEquals(tail, diskCache.requestContentBlockSync(url, 1));
        assertEquals(2, content.size());
    }

    @Test
    void unknownSizeAcceptsSequentialFullAndTailBlocksButRejectsMalformedBlocksAndGaps() {
        var content = backing(fileContent);
        var metadata = backing(fileMetadata);
        URL url = mockUrl("unknown-tail");
        Buf key = serialize(url);
        var initialMetadata = serializeMetadata(new DiskMetadata(-1, new BooleanArrayList()));
        metadata.put(key, initialMetadata);
        var invalid = List.of(
                DataBlock.of(-FileSponge.BLOCK_SIZE, 1, Buf.wrap(new byte[1])),
                DataBlock.of(1, 1, Buf.wrap(new byte[1])),
                DataBlock.of(0, -1, Buf.wrap(new byte[0])),
                DataBlock.of(0, FileSponge.BLOCK_SIZE + 1, Buf.wrap(new byte[FileSponge.BLOCK_SIZE + 1])),
                DataBlock.of(0, 3, Buf.wrap(new byte[2])));
        for (var block : invalid) {
            assertThrows(IllegalStateException.class, () -> diskCache.writeContentBlockSync(url, block, true));
        }
        assertThrows(IndexOutOfBoundsException.class, () -> diskCache.writeContentBlockSync(url,
                DataBlock.of(FileSponge.BLOCK_SIZE, 3, Buf.wrap(new byte[3])), true));
        assertTrue(content.isEmpty());
        assertEquals(initialMetadata, metadata.get(key));
        diskCache.writeContentBlockSync(url, DataBlock.of(0, FileSponge.BLOCK_SIZE,
                Buf.wrap(new byte[FileSponge.BLOCK_SIZE])), true);
        diskCache.writeContentBlockSync(url, DataBlock.of(FileSponge.BLOCK_SIZE, 3, Buf.wrap(new byte[3])), true);
        assertEquals(-1, diskCache.requestDiskMetadataSync(url).size());
        assertEquals(new BooleanArrayList(new boolean[]{true, true}), diskCache.requestDiskMetadataSync(url).downloadedBlocks());
        assertEquals(FileSponge.BLOCK_SIZE, content.get(getBlockKey(key, 0)).size());
        assertEquals(3, content.get(getBlockKey(key, 1)).size());
    }

    @Test
    void unknownSizeFormatLimitRejectsBlockBeforeWritingContent() {
        var content = backing(fileContent);
        var metadata = backing(fileMetadata);
        URL url = mockUrl("format-limit");
        Buf key = serialize(url);
        var original = serializeMetadata(new DiskMetadata(-1, new BooleanArrayList(new boolean[65535])));
        metadata.put(key, original);
        assertThrows(IllegalArgumentException.class, () -> diskCache.writeContentBlockSync(url,
                DataBlock.of(65535L * FileSponge.BLOCK_SIZE, 1, Buf.wrap(new byte[1])), true));
        assertTrue(content.isEmpty());
        assertEquals(original, metadata.get(key));
    }

    @Test
    void absentMetadataRejectsBlockBeforeWritingContent() {
        var content = backing(fileContent);
        var metadata = backing(fileMetadata);
        assertThrows(IllegalStateException.class, () -> diskCache.writeContentBlockSync(mockUrl("absent"),
                DataBlock.of(0, 3, Buf.wrap(new byte[3])), true));
        assertTrue(content.isEmpty());
        assertTrue(metadata.isEmpty());
    }

    @Test
    void unknownCachedPrefixFallsBackToProviderUntilMetadataBecomesKnown() {
        var content = backing(fileContent);
        backing(fileMetadata);
        URL url = mockUrl("interrupted-unknown");
        diskCache.writeMetadataSync(url, new Metadata(-1), true);
        diskCache.writeContentBlockSync(url, DataBlock.of(0, 2, Buf.wrap(new byte[]{1, 2})), true);
        assertFalse(diskCache.requestDiskMetadataSync(url).isDownloadedFully());
        assertEquals(0, diskCache.requestContentSync(url).count());
        var complete = DataBlock.of(0, 7, Buf.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7}));
        var provider = mock(URLsHandler.class);
        var providerKnowsSize = new java.util.concurrent.atomic.AtomicBoolean();
        when(provider.requestContent(url)).thenReturn(Flux.just(complete).doOnComplete(() -> providerKnowsSize.set(true)));
        when(provider.requestMetadata(url)).thenReturn(Mono.fromSupplier(() -> new Metadata(providerKnowsSize.get() ? 7 : -1)));
        var sponge = new FileSponge();
        sponge.registerCache(diskCache).block(Duration.ofSeconds(5));
        sponge.registerSource(provider).block(Duration.ofSeconds(5));
        assertEquals(-1, sponge.requestMetadata(url).block(Duration.ofSeconds(5)).size());
        assertEquals(List.of(complete), sponge.requestContent(url).collectList().block(Duration.ofSeconds(5)));
        assertEquals(complete.getData(), content.get(getBlockKey(serialize(url), 0)));
        assertFalse(diskCache.requestDiskMetadataSync(url).isDownloadedFully(), "Successful bytes do not establish an unknown total size");
        assertEquals(7, sponge.requestMetadata(url).block(Duration.ofSeconds(5)).size());
        assertFalse(diskCache.requestDiskMetadataSync(url).isDownloadedFully(), "Known metadata resets block completeness until validated writes finish");
        assertEquals(List.of(complete), sponge.requestContent(url).collectList().block(Duration.ofSeconds(5)));
        assertTrue(diskCache.requestDiskMetadataSync(url).isDownloadedFully());
        assertEquals(7, sponge.requestMetadata(url).block(Duration.ofSeconds(5)).size());
        assertEquals(List.of(complete), sponge.requestContent(url).collectList().block(Duration.ofSeconds(5)));
        verify(provider, times(3)).requestMetadata(url);
        verify(provider, times(2)).requestContent(url);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void unknownProviderBlocksWaitForPriorCacheWriteWhileKnownSizeWritesStayParallel() throws Exception {
        for (String mode : List.of("unknown", "known", "mixed")) {
            boolean knownSize = !mode.equals("unknown");
            boolean parallel = mode.equals("known");
            setUp();
            backing(fileContent);
            backing(fileMetadata);
            URL url = mockUrl("ordered-unknown-" + mode);
            diskCache.writeMetadataSync(url, new Metadata(knownSize ? FileSponge.BLOCK_SIZE + 7L : -1), true);
            var cache = spy(diskCache);
            var firstWrite = Sinks.<Void>one();
            var firstStarted = new java.util.concurrent.CountDownLatch(1);
            var secondStarted = new java.util.concurrent.CountDownLatch(1);
            var providerStarted = new java.util.concurrent.CountDownLatch(1);
            var subscribed = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
            doAnswer(invocation -> {
                DataBlock block = invocation.getArgument(1);
                return Mono.defer(() -> {
                    subscribed.add(block.getId());
                    if (block.getId() == 0) firstStarted.countDown();
                    else if (block.getId() == 1) secondStarted.countDown();
                    return (block.getId() == 0 ? firstWrite.asMono() : Mono.<Void>empty())
                            .then(Mono.fromRunnable(() -> diskCache.writeContentBlockSync(url, block, false)));
                });
            }).when(cache).writeContentBlock(eq(url), any(DataBlock.class), eq(false));
            var parts = Sinks.many().unicast().<DataBlock>onBackpressureBuffer();
            var provider = mock(URLsHandler.class);
            when(provider.requestContent(url)).thenReturn(parts.asFlux().doOnSubscribe(_ -> providerStarted.countDown()));
            // Metadata initialization is covered separately; isolate the per-writer ordering decision.
            var sponge = new FileSponge() {
                @Override public Mono<Metadata> requestMetadata(URL requested) {
                    assertEquals(url, requested);
                    return Mono.just(new Metadata(knownSize ? FileSponge.BLOCK_SIZE + 7L : -1));
                }
            };
            sponge.registerCache(cache).block(Duration.ofSeconds(5));
            if (mode.equals("mixed")) {
                var otherContent = mock(LLDictionary.class);
                var otherMetadata = mock(LLDictionary.class);
                backing(otherContent);
                backing(otherMetadata);
                var unknownCache = new DiskCacheImpl(null, otherContent, otherMetadata, fileAliases, fileHashes, shouldCache);
                unknownCache.writeMetadataSync(url, new Metadata(-1), true);
                sponge.registerCache(unknownCache).block(Duration.ofSeconds(5));
            }
            sponge.registerSource(provider).block(Duration.ofSeconds(5));
            var first = DataBlock.of(0, FileSponge.BLOCK_SIZE, Buf.wrap(new byte[FileSponge.BLOCK_SIZE]));
            var tail = DataBlock.of(FileSponge.BLOCK_SIZE, 7, Buf.wrap(new byte[7]));
            var result = sponge.requestContent(url).collectList().toFuture();
            try {
                assertTrue(providerStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
                parts.tryEmitNext(first).orThrow();
                assertTrue(firstStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
                parts.tryEmitNext(tail).orThrow();
                parts.tryEmitComplete().orThrow();
                if (parallel) assertTrue(secondStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
                else assertFalse(secondStarted.await(100, java.util.concurrent.TimeUnit.MILLISECONDS), "Unknown-size writers must wait for block zero");
                assertEquals(parallel ? List.of(0, 1) : List.of(0), subscribed);
                assertFalse(result.isDone(), "Output waits for the first physical cache write");
                firstWrite.tryEmitEmpty().orThrow();
                assertEquals(List.of(first, tail), result.get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(List.of(0, 1), subscribed);
                assertEquals(new BooleanArrayList(new boolean[]{true, true}), diskCache.requestDiskMetadataSync(url).downloadedBlocks());
                assertEquals(knownSize, diskCache.requestDiskMetadataSync(url).isDownloadedFully());
            } finally { result.cancel(true); }
        }
    }

    @Test
    void unknownEmptyAndPartialMetadataAreIncompleteButKnownEmptyContentIsComplete() {
        assertFalse(new DiskMetadata(-1, new BooleanArrayList()).isDownloadedFully());
        assertFalse(new DiskMetadata(-1, new BooleanArrayList(new boolean[]{true})).isDownloadedFully());
        assertFalse(new DiskMetadata(-1, new BooleanArrayList(new boolean[]{false})).isDownloadedFully());
        assertTrue(new DiskMetadata(0, new BooleanArrayList()).isDownloadedFully());
        URL url = mockUrl("known-empty");
        when(fileMetadata.get(null, serialize(url))).thenReturn(serializeMetadata(new DiskMetadata(0, new BooleanArrayList())));
        assertEquals(0, diskCache.requestContentSync(url).count());
        verifyNoInteractions(fileContent);
    }

    @Test
    void fullCachedReadsRejectTruncatedFirstOrLastAndMissingBlocks() {
        var content = backing(fileContent);
        var metadata = backing(fileMetadata);
        URL url = mockUrl("corrupted-full-cache");
        Buf key = serialize(url);
        metadata.put(key, serializeMetadata(new DiskMetadata(FileSponge.BLOCK_SIZE + 3L,
                new BooleanArrayList(new boolean[]{true, true}))));
        for (String corruption : List.of("first", "last", "missing-first", "missing-last")) {
            content.clear();
            if (!corruption.equals("missing-first")) content.put(getBlockKey(key, 0),
                    Buf.wrap(new byte[corruption.equals("first") ? FileSponge.BLOCK_SIZE - 1 : FileSponge.BLOCK_SIZE]));
            if (!corruption.equals("missing-last")) content.put(getBlockKey(key, 1),
                    Buf.wrap(new byte[corruption.equals("last") ? 2 : 3]));
            try (var blocks = diskCache.requestContentSync(url)) {
                var iterator = blocks.iterator();
                if (corruption.equals("last") || corruption.equals("missing-last")) {
                    assertEquals(FileSponge.BLOCK_SIZE, iterator.next().getLength());
                }
                var error = assertThrows(IllegalStateException.class, iterator::next);
                assertTrue(error.getMessage().contains(corruption.startsWith("missing") ? "Missing cached file block" : "Invalid or truncated file block"));
            }
        }
    }

    @Test
    void testDeleteWithAlias() {
        URL originalUrl = mockUrl("original");
        URL aliasUrl = mockUrl("alias");
        Buf originalKey = serialize(originalUrl);
        Buf aliasKey = serialize(aliasUrl);

        // Setup alias: alias -> original
        when(fileAliases.get(null, aliasKey)).thenReturn(originalKey);

        // Setup metadata for original
        DiskMetadata meta = new DiskMetadata(1024, new BooleanArrayList(new boolean[]{true}));
        Buf metaBuf = serializeMetadata(meta);
        when(fileMetadata.get(null, originalKey)).thenReturn(metaBuf);

        // ACT: delete alias
        diskCache.deleteContentSync(aliasUrl);

        // VERIFY: it should have deleted from originalKey
        verify(fileMetadata).remove(eq(originalKey), any());
    }

    @Test
    void testGetBlocksCount() {
        int blockSize = 1024;
        assertEquals(0, DiskMetadata.getBlocksCount(-1, blockSize));
        assertEquals(0, DiskMetadata.getBlocksCount(0, blockSize));
        assertEquals(1, DiskMetadata.getBlocksCount(1, blockSize));
        assertEquals(1, DiskMetadata.getBlocksCount(1024, blockSize));
        assertEquals(2, DiskMetadata.getBlocksCount(1025, blockSize));
    }

    @Test
    void testWriteMetadataTwice() {
        URL url = mockUrl("test");
        Buf key = serialize(url);
        Metadata meta1 = new Metadata(100);
        Metadata meta2 = new Metadata(200);

        // 1. Write first metadata
        diskCache.writeMetadataSync(url, meta1, false);

        // Capture update lambda
        ArgumentCaptor<SerializationFunction<Buf, Buf>> captor = ArgumentCaptor.forClass(SerializationFunction.class);
        verify(fileMetadata).update(eq(key), captor.capture(), any());

        // Simulate update on empty value
        Buf result1 = captor.getValue().apply(null);
        assertNotNull(result1);

        // 2. Write second metadata (different size)
        reset(fileMetadata);
        diskCache.writeMetadataSync(url, meta2, false);
        verify(fileMetadata).update(eq(key), captor.capture(), any());

        // Simulate update on existing value (old result1)
        Buf result2 = captor.getValue().apply(result1);

        // If bug exists, result2 is same as result1 (ignoring meta2)
        DiskMetadata diskMeta2 = deserializeMetadata(result2);
        assertEquals(200, diskMeta2.size(), "Metadata size should be updated if different");
    }

    @Test
    void testWriteAndGetHash() {
        URL url1 = mockUrl("url1");
        URL url2 = mockUrl("url2");
        Buf key1 = serialize(url1);
        Buf key2 = serialize(url2);

        // write hash 1
        diskCache.writeHashSync(url1, 100L);
        // write hash 2
        diskCache.writeHashSync(url2, 200L);

        // We capture what was put in fileHashes
        ArgumentCaptor<Buf> hashKeyCaptor = ArgumentCaptor.forClass(Buf.class);
        ArgumentCaptor<Buf> urlKeyCaptor = ArgumentCaptor.forClass(Buf.class);
        verify(fileHashes, times(2)).put(hashKeyCaptor.capture(), urlKeyCaptor.capture(), eq(LLDictionaryResultType.VOID));

        Buf buf1 = hashKeyCaptor.getAllValues().get(0);
        Buf buf2 = hashKeyCaptor.getAllValues().get(1);

        System.out.println("buf1 size: " + buf1.size());
        System.out.println("buf2 size: " + buf2.size());
        
        System.out.println("buf1 content: " + buf1);
        System.out.println("buf2 content: " + buf2);
        
        System.out.println("buf1 hashcode: " + buf1.hashCode());
        System.out.println("buf2 hashcode: " + buf2.hashCode());
        
        assertNotEquals(buf1, buf2, "Hashes buffers should be different");
    }

    @Test
    void testGetUrlByHash() {
        URL url1 = mockUrl("url1");
        URL url2 = mockUrl("url2");
        Buf key1 = serialize(url1);
        Buf key2 = serialize(url2);

        // write hash 1
        diskCache.writeHashSync(url1, 100L);
        // write hash 2
        diskCache.writeHashSync(url2, 200L);

        // Mock the dictionary behavior for getUrlByHashSync
        when(fileHashes.get(eq(null), any(Buf.class))).thenAnswer(inv -> {
            Buf hashBuf = inv.getArgument(1);
            // check what's inside hashBuf
            byte[] bytes = hashBuf.asArray();
            long hash = java.nio.ByteBuffer.wrap(bytes).getLong();
            if (hash == 100L) return key1;
            if (hash == 200L) return key2;
            return null;
        });

        Buf result1 = diskCache.getUrlByHashSync(100L);
        Buf result2 = diskCache.getUrlByHashSync(200L);

        assertEquals(key1, result1, "Hash 100 should return url1");
        assertEquals(key2, result2, "Hash 200 should return url2");
    }

    @Test
    void testSerializeUrl() {
        URL url1 = mockUrl("a");
        URL url2 = mockUrl("b");
        
        // This will call serialize
        Buf key1 = serialize(url1);
        Buf key2 = serialize(url2);

        System.out.println("key1 size: " + key1.size());
        System.out.println("key2 size: " + key2.size());
        
        System.out.println("key1: " + key1);
        System.out.println("key2: " + key2);
        
        assertNotEquals(key1, key2, "Serialized URLs should be different");
    }

    @Test
    void testWriteHashAndAlias() {
        URL url1 = mockUrl("clown");
        URL url2 = mockUrl("other");

        Buf key1 = serialize(url1);
        Buf key2 = serialize(url2);

        // write clown picture hash
        diskCache.writeHashSync(url1, 12345L);
        
        // mock fileHashes to return key1 for 12345L
        when(fileHashes.get(eq(null), any(Buf.class))).thenAnswer(inv -> {
            Buf hashBuf = inv.getArgument(1);
            long hash = java.nio.ByteBuffer.wrap(hashBuf.asArray()).getLong();
            if (hash == 12345L) return key1;
            return null;
        });

        // Now we get other picture hash
        diskCache.writeHashSync(url2, 99999L);

        // check getUrlByHashSync
        Buf existing1 = diskCache.getUrlByHashSync(12345L);
        Buf existing2 = diskCache.getUrlByHashSync(99999L);
        
        assertEquals(key1, existing1);
        assertNull(existing2);
    }

    @Test
    void testGetBlockKey() {
        URL url1 = mockUrl("39700");
        URL url2 = mockUrl("39701");
        Buf key1 = serialize(url1);
        Buf key2 = serialize(url2);

        Buf blockKey1 = getBlockKey(key1, 0);
        Buf blockKey2 = getBlockKey(key2, 0);

        System.out.println("blockKey1 size: " + blockKey1.size());
        System.out.println("blockKey2 size: " + blockKey2.size());
        System.out.println("blockKey1 content: " + blockKey1);
        System.out.println("blockKey2 content: " + blockKey2);

        assertNotEquals(blockKey1, blockKey2, "Block keys should be different");
    }

    @Test
    void testSerializeStoredFileURL() {
        URL url1 = new URL() {
            @Override
            public URLSerializer<?> getSerializer() {
                return new URLStringSerializer<URL>() {
                    @Override
                    public @org.jetbrains.annotations.NotNull String serialize(@org.jetbrains.annotations.NotNull URL url) {
                        return "filesponge://storage/39700";
                    }
                };
            }
        };
        URL url2 = new URL() {
            @Override
            public URLSerializer<?> getSerializer() {
                return new URLStringSerializer<URL>() {
                    @Override
                    public @org.jetbrains.annotations.NotNull String serialize(@org.jetbrains.annotations.NotNull URL url) {
                        return "filesponge://storage/39701";
                    }
                };
            }
        };

        Buf key1 = serialize(url1);
        Buf key2 = serialize(url2);

        System.out.println("StoredFileURL key1 size: " + key1.size());
        System.out.println("StoredFileURL key2 size: " + key2.size());
        System.out.println("StoredFileURL key1 content: " + key1);
        System.out.println("StoredFileURL key2 content: " + key2);

        assertNotEquals(key1, key2, "Serialized StoredFileURLs should be different");
    }

    @SuppressWarnings("unchecked")
    private Map<Buf, Buf> backing(LLDictionary dictionary) {
        var values = new HashMap<Buf, Buf>();
        when(dictionary.get(eq(null), any(Buf.class))).thenAnswer(invocation -> values.get(invocation.getArgument(1)));
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(dictionary).put(any(Buf.class), any(Buf.class), eq(LLDictionaryResultType.VOID));
        doAnswer(invocation -> {
            Buf key = invocation.getArgument(0);
            SerializationFunction<Buf, Buf> updater = invocation.getArgument(1);
            values.put(key, updater.apply(values.get(key)));
            return null;
        }).when(dictionary).update(any(Buf.class), any(), eq(UpdateReturnMode.NOTHING));
        return values;
    }

    private Buf getBlockKey(Buf urlKey, int blockId) {
        var sizeHint = urlKey.size() + Integer.BYTES;
        BufDataOutput out = BufDataOutput.create(sizeHint);
        out.writeBytes(urlKey);
        out.writeInt(blockId);
        return out.asList();
    }

    private DiskMetadata deserializeMetadata(Buf buf) {
        try {
            return new DiskMetadata.DiskMetadataSerializer().deserialize(DatabaseDataInput.create(buf));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private URL mockUrl(String val) {
        URL url = mock(URL.class);
        URLSerializer serializer = mock(URLSerializer.class);
        when(url.getSerializer()).thenReturn(serializer);
        doAnswer(inv -> {
            BufDataOutput out = inv.getArgument(1);
            out.writeUTF(val);
            return null;
        }).when(serializer).serialize(any(), any());
        return url;
    }

    private Buf serialize(URL url) {
        BufDataOutput out = BufDataOutput.create(64);
        try {
            URLSerializer serializer = url.getSerializer();
            serializer.serialize(url, out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out.asList();
    }

    private Buf serializeMetadata(DiskMetadata meta) {
        BufDataOutput out = BufDataOutput.create(64);
        try {
            new DiskMetadata.DiskMetadataSerializer().serialize(meta, out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out.asList();
    }
}
