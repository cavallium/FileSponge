package org.warp.filesponge;

import static org.junit.jupiter.api.Assertions.*;

import it.cavallium.dbengine.database.serialization.DatabaseDataInput;
import it.cavallium.buffer.BufDataOutput;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import org.junit.jupiter.api.Test;

class DiskMetadataTest {
    @Test
    void unknownSizeIsIncompleteEvenWhenAllReceivedBlocksArePresent() {
        assertFalse(new DiskMetadata(-1, new BooleanArrayList()).isDownloadedFully());
        assertFalse(new DiskMetadata(-1, new BooleanArrayList(new boolean[]{true})).isDownloadedFully());
        assertTrue(new DiskMetadata(0, new BooleanArrayList()).isDownloadedFully());
    }

    @Test
    void blockCountRejectsInvalidAndUnrepresentableSizesWithoutOverflow() {
        assertThrows(IllegalArgumentException.class, () -> DiskMetadata.getBlocksCount(-2, FileSponge.BLOCK_SIZE));
        assertThrows(IllegalArgumentException.class, () -> DiskMetadata.getBlocksCount(1, 0));
        assertThrows(ArithmeticException.class, () -> DiskMetadata.getBlocksCount(Long.MAX_VALUE, FileSponge.BLOCK_SIZE));
        assertEquals(Integer.MAX_VALUE, DiskMetadata.getBlocksCount(
                (long) Integer.MAX_VALUE * FileSponge.BLOCK_SIZE, FileSponge.BLOCK_SIZE));
    }

    @Test
    void unknownSizeBlockCountRoundTripsAcrossSignedShortBoundary() {
        var serializer = new DiskMetadata.DiskMetadataSerializer();
        for (int count : new int[]{32767, 32768, 65535}) {
            var flags = new BooleanArrayList(new boolean[count]);
            flags.set(count - 1, true);
            var metadata = new DiskMetadata(-1, flags);
            var out = BufDataOutput.create(16 + count);
            serializer.serialize(metadata, out);
            assertEquals(metadata, serializer.deserialize(DatabaseDataInput.create(out.asList())));
        }
        var oversized = new DiskMetadata(-1, new BooleanArrayList(new boolean[65536]));
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(oversized, BufDataOutput.create(16)));
    }
}
