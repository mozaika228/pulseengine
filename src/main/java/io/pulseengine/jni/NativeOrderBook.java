package io.pulseengine.jni;

public final class NativeOrderBook implements AutoCloseable {
    public static final int CLIENT_API_VERSION = 2;
    public static final int CLIENT_MIN_COMPATIBLE_API_VERSION = 1;

    public static final int INSERT_OK = 0;
    public static final int INSERT_INVALID_QTY = 1;
    public static final int INSERT_BOOK_LEVELS_FULL = 2;
    public static final int INSERT_ORDER_POOL_EXHAUSTED = 3;

    private static final Throwable LOAD_ERROR;
    private static final int LOADED_API_VERSION;
    private static final int LOADED_MIN_COMPATIBLE_API_VERSION;
    private static final int LOADED_LAYOUT_VERSION;
    private static final int LOADED_LAYOUT_HASH;

    static {
        Throwable loadError = null;
        int apiVersion = -1;
        int minCompatibleApiVersion = -1;
        int layoutVersion = -1;
        int layoutHash = -1;
        try {
            System.loadLibrary("pulseengine_native");
            apiVersion = nativeApiVersion();
            minCompatibleApiVersion = nativeMinCompatibleApiVersion();
            layoutVersion = nativeCommandLayoutVersion();
            layoutHash = nativeCommandLayoutHash();
            verifyCompatibility(apiVersion, minCompatibleApiVersion, layoutVersion, layoutHash);
        } catch (Throwable t) {
            loadError = t;
        }
        LOAD_ERROR = loadError;
        LOADED_API_VERSION = apiVersion;
        LOADED_MIN_COMPATIBLE_API_VERSION = minCompatibleApiVersion;
        LOADED_LAYOUT_VERSION = layoutVersion;
        LOADED_LAYOUT_HASH = layoutHash;
    }

    private long nativeHandle;

    public NativeOrderBook() {
        this(1024, 16384);
    }

    public NativeOrderBook(int maxLevels, int maxOrders) {
        ensureNativeAvailable();
        if (maxLevels <= 0 || maxOrders <= 0) {
            throw new IllegalArgumentException("maxLevels and maxOrders must be positive");
        }
        nativeHandle = nativeCreateWithCapacity(maxLevels, maxOrders);
        if (nativeHandle == 0) {
            throw new IllegalStateException("Failed to create native order book");
        }
    }

    public void insertLimitOrder(long orderId, double price, long qty, boolean isBuy) {
        int status = tryInsertLimitOrder(orderId, price, qty, isBuy);
        ensureInsertOk(status, "insertLimitOrder");
    }

    public int tryInsertLimitOrder(long orderId, double price, long qty, boolean isBuy) {
        ensureOpen();
        return nativeInsertLimitOrder(nativeHandle, orderId, price, qty, isBuy);
    }

    public void insertLimitIceberg(long orderId, double price, long qty, long peakQty, boolean isBuy) {
        int status = tryInsertLimitIceberg(orderId, price, qty, peakQty, isBuy);
        ensureInsertOk(status, "insertLimitIceberg");
    }

    public int tryInsertLimitIceberg(long orderId, double price, long qty, long peakQty, boolean isBuy) {
        ensureOpen();
        return nativeInsertLimitIceberg(nativeHandle, orderId, price, qty, peakQty, isBuy);
    }

    public MatchResult matchMarketOrder(long orderId, long qty, boolean isBuy) {
        ensureOpen();
        return nativeMatchMarketOrder(nativeHandle, orderId, qty, isBuy);
    }

    public L2Update publishL2Update() {
        ensureOpen();
        return nativePublishL2Update(nativeHandle);
    }

    @Override
    public void close() {
        long handle = nativeHandle;
        nativeHandle = 0;
        if (handle != 0) {
            nativeDestroy(handle);
        }
    }

    private void ensureOpen() {
        ensureNativeAvailable();
        if (nativeHandle == 0) {
            throw new IllegalStateException("Native order book is closed");
        }
    }

    public static boolean isNativeAvailable() {
        return LOAD_ERROR == null;
    }

    public static int loadedApiVersion() {
        return LOADED_API_VERSION;
    }

    public static int loadedMinCompatibleApiVersion() {
        return LOADED_MIN_COMPATIBLE_API_VERSION;
    }

    public static int loadedCommandLayoutVersion() {
        return LOADED_LAYOUT_VERSION;
    }

    public static int loadedCommandLayoutHash() {
        return LOADED_LAYOUT_HASH;
    }

    private static void ensureNativeAvailable() {
        if (LOAD_ERROR != null) {
            throw new IllegalStateException("Native library is unavailable or ABI-incompatible", LOAD_ERROR);
        }
    }

    private static void verifyCompatibility(
        int apiVersion,
        int minCompatibleApiVersion,
        int commandLayoutVersion,
        int commandLayoutHash
    ) {
        if (CLIENT_API_VERSION > apiVersion || CLIENT_API_VERSION < minCompatibleApiVersion) {
            throw new IllegalStateException(
                "Native API version mismatch: client=" + CLIENT_API_VERSION
                    + " native_api=" + apiVersion
                    + " native_min_compat=" + minCompatibleApiVersion
            );
        }
        if (commandLayoutVersion != NativeOrderBinaryLayout.VERSION) {
            throw new IllegalStateException(
                "Native binary layout version mismatch: client=" + NativeOrderBinaryLayout.VERSION
                    + " native=" + commandLayoutVersion
            );
        }
        if (commandLayoutHash != NativeOrderBinaryLayout.HASH) {
            throw new IllegalStateException(
                "Native binary layout hash mismatch: client=" + NativeOrderBinaryLayout.HASH
                    + " native=" + commandLayoutHash
            );
        }
    }

    private static void ensureInsertOk(int status, String operation) {
        if (status != INSERT_OK) {
            throw new IllegalStateException(operation + " rejected by native core, status=" + status);
        }
    }

    private static native long nativeCreate();

    private static native long nativeCreateWithCapacity(int maxLevels, int maxOrders);

    private static native void nativeDestroy(long handle);

    private static native int nativeInsertLimitOrder(long handle, long orderId, double price, long qty, boolean isBuy);

    private static native int nativeInsertLimitIceberg(long handle, long orderId, double price, long qty, long peakQty, boolean isBuy);

    private static native MatchResult nativeMatchMarketOrder(long handle, long orderId, long qty, boolean isBuy);

    private static native L2Update nativePublishL2Update(long handle);

    private static native int nativeApiVersion();

    private static native int nativeMinCompatibleApiVersion();

    private static native int nativeCommandLayoutVersion();

    private static native int nativeCommandLayoutHash();

    public static final class MatchResult {
        public final long filledQty;
        public final long remainingQty;
        public final int trades;
        public final double avgPrice;
        public final double lastTradePrice;

        public MatchResult(long filledQty, long remainingQty, int trades, double avgPrice, double lastTradePrice) {
            this.filledQty = filledQty;
            this.remainingQty = remainingQty;
            this.trades = trades;
            this.avgPrice = avgPrice;
            this.lastTradePrice = lastTradePrice;
        }
    }

    public static final class L2Update {
        public final double bestBid;
        public final long bestBidQty;
        public final double bestAsk;
        public final long bestAskQty;

        public L2Update(double bestBid, long bestBidQty, double bestAsk, long bestAskQty) {
            this.bestBid = bestBid;
            this.bestBidQty = bestBidQty;
            this.bestAsk = bestAsk;
            this.bestAskQty = bestAskQty;
        }
    }
}