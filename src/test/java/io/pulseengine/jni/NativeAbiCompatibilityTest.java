package io.pulseengine.jni;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeAbiCompatibilityTest {

    @Test
    void javaSideContractIsPinned() {
        assertEquals(2, NativeOrderBook.CLIENT_API_VERSION);
        assertEquals(1, NativeOrderBook.CLIENT_MIN_COMPATIBLE_API_VERSION);
        assertEquals(1, NativeOrderBinaryLayout.VERSION);
        assertEquals(-1916503777, NativeOrderBinaryLayout.HASH);
    }

    @Test
    void nativeLibraryCompatibilityIsValidatedWhenAvailable() {
        Assumptions.assumeTrue(
            NativeOrderBook.isNativeAvailable(),
            "Native library not loaded in test runtime"
        );

        assertTrue(NativeOrderBook.loadedApiVersion() >= NativeOrderBook.CLIENT_API_VERSION);
        assertTrue(NativeOrderBook.loadedMinCompatibleApiVersion() <= NativeOrderBook.CLIENT_API_VERSION);
        assertEquals(NativeOrderBinaryLayout.VERSION, NativeOrderBook.loadedCommandLayoutVersion());
        assertEquals(NativeOrderBinaryLayout.HASH, NativeOrderBook.loadedCommandLayoutHash());
    }
}
