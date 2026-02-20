package io.pulseengine.md;

public interface MdMessageSink {
    void onMessage(byte[] buffer, int offset, int length);
}
