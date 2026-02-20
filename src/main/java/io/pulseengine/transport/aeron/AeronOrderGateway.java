package io.pulseengine.transport.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.pulseengine.core.Side;
import io.pulseengine.core.TimeInForce;
import org.agrona.concurrent.UnsafeBuffer;

public final class AeronOrderGateway implements AutoCloseable {
    private final Publication publication;
    private final UnsafeBuffer buffer = AeronOrderCodec.newBuffer();

    public AeronOrderGateway(Aeron aeron) {
        this(aeron, AeronChannels.IPC_CHANNEL, AeronChannels.ORDERS_STREAM_ID);
    }

    public AeronOrderGateway(Aeron aeron, String channel, int streamId) {
        this.publication = aeron.addPublication(channel, streamId);
    }

    public void submitLimit(long orderId, long traderId, Side side, long price, long quantity, TimeInForce tif, long peak) {
        AeronOrderCodec.encode(
            buffer,
            AeronOrderCommand.TYPE_LIMIT,
            mapSide(side),
            mapTif(tif),
            orderId,
            traderId,
            price,
            0,
            quantity,
            peak,
            0
        );
        offer();
    }

    public boolean trySubmitLimit(long orderId, long traderId, Side side, long price, long quantity, TimeInForce tif, long peak) {
        AeronOrderCodec.encode(
            buffer,
            AeronOrderCommand.TYPE_LIMIT,
            mapSide(side),
            mapTif(tif),
            orderId,
            traderId,
            price,
            0,
            quantity,
            peak,
            0
        );
        return publication.offer(buffer, 0, AeronOrderCommand.FIXED_LENGTH) >= 0;
    }

    public void submitMarket(long orderId, long traderId, Side side, long quantity, TimeInForce tif) {
        AeronOrderCodec.encode(
            buffer,
            AeronOrderCommand.TYPE_MARKET,
            mapSide(side),
            mapTif(tif),
            orderId,
            traderId,
            0,
            0,
            quantity,
            0,
            0
        );
        offer();
    }

    public boolean trySubmitMarket(long orderId, long traderId, Side side, long quantity, TimeInForce tif) {
        AeronOrderCodec.encode(
            buffer,
            AeronOrderCommand.TYPE_MARKET,
            mapSide(side),
            mapTif(tif),
            orderId,
            traderId,
            0,
            0,
            quantity,
            0,
            0
        );
        return publication.offer(buffer, 0, AeronOrderCommand.FIXED_LENGTH) >= 0;
    }

    public void submitStopMarket(long orderId, long traderId, Side side, long stopPrice, long quantity) {
        AeronOrderCodec.encode(
            buffer,
            AeronOrderCommand.TYPE_STOP_MARKET,
            mapSide(side),
            AeronOrderCommand.TIF_GTC,
            orderId,
            traderId,
            0,
            stopPrice,
            quantity,
            0,
            0
        );
        offer();
    }

    public boolean trySubmitStopMarket(long orderId, long traderId, Side side, long stopPrice, long quantity) {
        AeronOrderCodec.encode(
            buffer,
            AeronOrderCommand.TYPE_STOP_MARKET,
            mapSide(side),
            AeronOrderCommand.TIF_GTC,
            orderId,
            traderId,
            0,
            stopPrice,
            quantity,
            0,
            0
        );
        return publication.offer(buffer, 0, AeronOrderCommand.FIXED_LENGTH) >= 0;
    }

    public void submitCancel(long cancelOrderId) {
        AeronOrderCodec.encode(
            buffer,
            AeronOrderCommand.TYPE_CANCEL,
            AeronOrderCommand.SIDE_BUY,
            AeronOrderCommand.TIF_GTC,
            0,
            0,
            0,
            0,
            0,
            0,
            cancelOrderId
        );
        offer();
    }

    public boolean trySubmitCancel(long cancelOrderId) {
        AeronOrderCodec.encode(
            buffer,
            AeronOrderCommand.TYPE_CANCEL,
            AeronOrderCommand.SIDE_BUY,
            AeronOrderCommand.TIF_GTC,
            0,
            0,
            0,
            0,
            0,
            0,
            cancelOrderId
        );
        return publication.offer(buffer, 0, AeronOrderCommand.FIXED_LENGTH) >= 0;
    }

    private void offer() {
        while (publication.offer(buffer, 0, AeronOrderCommand.FIXED_LENGTH) < 0) {
            Thread.onSpinWait();
        }
    }

    private static byte mapSide(Side side) {
        return side == Side.SELL ? AeronOrderCommand.SIDE_SELL : AeronOrderCommand.SIDE_BUY;
    }

    private static byte mapTif(TimeInForce tif) {
        return switch (tif) {
            case IOC -> AeronOrderCommand.TIF_IOC;
            case FOK -> AeronOrderCommand.TIF_FOK;
            default -> AeronOrderCommand.TIF_GTC;
        };
    }

    @Override
    public void close() {
        publication.close();
    }

    public boolean isConnected() {
        return publication.isConnected();
    }
}
