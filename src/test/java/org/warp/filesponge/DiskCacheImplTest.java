package org.warp.filesponge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import it.cavallium.buffer.Buf;
import it.cavallium.buffer.BufDataOutput;
import it.cavallium.dbengine.database.LLDictionary;
import it.cavallium.dbengine.database.LLDictionaryResultType;
import it.cavallium.dbengine.database.UpdateReturnMode;
import it.cavallium.dbengine.database.serialization.SerializationFunction;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

    private Buf getBlockKey(Buf urlKey, int blockId) {
        var sizeHint = urlKey.size() + Integer.BYTES;
        BufDataOutput out = BufDataOutput.create(sizeHint);
        out.writeBytes(urlKey);
        out.writeInt(blockId);
        return out.asList();
    }

    private DiskMetadata deserializeMetadata(Buf buf) {
        try {
            return new DiskMetadata.DiskMetadataSerializer().deserialize(it.cavallium.buffer.BufDataInput.create(buf));
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
