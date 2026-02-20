package io.pulseengine.md;

public final class TopOfBookView {
    private volatile long bestBid;
    private volatile long bestAsk;
    private volatile long sequence;

    public void publish(long bid, long ask) {
        this.bestBid = bid;
        this.bestAsk = ask;
        this.sequence++;
    }

    public long bestBid() {
        return bestBid;
    }

    public long bestAsk() {
        return bestAsk;
    }

    public long sequence() {
        return sequence;
    }
}
