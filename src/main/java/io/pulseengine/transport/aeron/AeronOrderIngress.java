package io.pulseengine.transport.aeron;

import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.pulseengine.core.Side;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.pipeline.EnginePipeline;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class AeronOrderIngress {
    private final Subscription subscription;
    private final EnginePipeline engine;
    private final UnsafeBuffer decodeBuffer = new UnsafeBuffer(0, 0);
    private final FragmentHandler handler;

    public AeronOrderIngress(Subscription subscription, EnginePipeline engine) {
        this.subscription = subscription;
        this.engine = engine;
        this.handler = (buffer, offset, length, header) -> {
            if (length < AeronOrderCommand.FIXED_LENGTH) {
                return;
            }
            decodeBuffer.wrap((DirectBuffer) buffer, 0, buffer.capacity());

            byte type = AeronOrderCodec.type(decodeBuffer, offset);
            switch (type) {
                case AeronOrderCommand.TYPE_LIMIT -> engine.submitLimit(
                    AeronOrderCodec.orderId(decodeBuffer, offset),
                    AeronOrderCodec.traderId(decodeBuffer, offset),
                    mapSide(AeronOrderCodec.side(decodeBuffer, offset)),
                    AeronOrderCodec.price(decodeBuffer, offset),
                    AeronOrderCodec.quantity(decodeBuffer, offset),
                    mapTif(AeronOrderCodec.tif(decodeBuffer, offset)),
                    AeronOrderCodec.peak(decodeBuffer, offset)
                );
                case AeronOrderCommand.TYPE_MARKET -> engine.submitMarket(
                    AeronOrderCodec.orderId(decodeBuffer, offset),
                    AeronOrderCodec.traderId(decodeBuffer, offset),
                    mapSide(AeronOrderCodec.side(decodeBuffer, offset)),
                    AeronOrderCodec.quantity(decodeBuffer, offset),
                    mapTif(AeronOrderCodec.tif(decodeBuffer, offset))
                );
                case AeronOrderCommand.TYPE_STOP_MARKET -> engine.submitStopMarket(
                    AeronOrderCodec.orderId(decodeBuffer, offset),
                    AeronOrderCodec.traderId(decodeBuffer, offset),
                    mapSide(AeronOrderCodec.side(decodeBuffer, offset)),
                    AeronOrderCodec.stopPrice(decodeBuffer, offset),
                    AeronOrderCodec.quantity(decodeBuffer, offset)
                );
                case AeronOrderCommand.TYPE_CANCEL -> engine.submitCancel(
                    AeronOrderCodec.cancelOrderId(decodeBuffer, offset)
                );
                default -> {
                }
            }
        };
    }

    public int poll(int fragmentLimit) {
        return subscription.poll(handler, fragmentLimit);
    }

    private static Side mapSide(byte side) {
        return side == AeronOrderCommand.SIDE_SELL ? Side.SELL : Side.BUY;
    }

    private static TimeInForce mapTif(byte tif) {
        return switch (tif) {
            case AeronOrderCommand.TIF_IOC -> TimeInForce.IOC;
            case AeronOrderCommand.TIF_FOK -> TimeInForce.FOK;
            default -> TimeInForce.GTC;
        };
    }
}
