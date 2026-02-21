package io.pulseengine.transport.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;

public final class AeronMdSink implements io.pulseengine.md.MdMessageSink, AutoCloseable {
    private static final int DEFAULT_MAX_OFFER_ATTEMPTS = 1_024;

    private final Publication publication;
    private final UnsafeBuffer sendBuffer = new UnsafeBuffer(0, 0);
    private final int maxOfferAttempts;

    public AeronMdSink(Aeron aeron) {
        this(aeron, AeronChannels.IPC_CHANNEL, AeronChannels.MD_STREAM_ID, DEFAULT_MAX_OFFER_ATTEMPTS);
    }

    public AeronMdSink(Aeron aeron, String channel, int streamId) {
        this(aeron, channel, streamId, DEFAULT_MAX_OFFER_ATTEMPTS);
    }

    public AeronMdSink(Aeron aeron, String channel, int streamId, int maxOfferAttempts) {
        this.publication = aeron.addPublication(channel, streamId);
        this.maxOfferAttempts = Math.max(1, maxOfferAttempts);
    }

    @Override
    public void onMessage(byte[] buffer, int offset, int length) {
        if (!tryOnMessage(buffer, offset, length)) {
            throw new IllegalStateException("Aeron market-data publication backpressured after attempts=" + maxOfferAttempts);
        }
    }

    public boolean tryOnMessage(byte[] buffer, int offset, int length) {
        sendBuffer.wrap(buffer);
        int attempts = 0;
        while (publication.offer(sendBuffer, offset, length) < 0) {
            attempts++;
            if (attempts >= maxOfferAttempts) {
                return false;
            }
            Thread.onSpinWait();
        }
        return true;
    }

    @Override
    public void close() {
        publication.close();
    }

    public boolean isConnected() {
        return publication.isConnected();
    }
}
