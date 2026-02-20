package io.pulseengine.persistence;

import io.pulseengine.core.OrderType;
import io.pulseengine.core.Side;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.pipeline.EnginePipeline;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class JournalReplayer {
    private JournalReplayer() {
    }

    public static long replay(Path path, EnginePipeline engine) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(JournalCodec.RECORD_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
        long replayed = 0;

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            while (true) {
                buffer.clear();
                int bytes = 0;
                while (bytes < JournalCodec.RECORD_LENGTH) {
                    int read = channel.read(buffer);
                    if (read < 0) {
                        if (bytes == 0) {
                            return replayed;
                        }
                        throw new IllegalStateException("Corrupt journal: partial record at EOF");
                    }
                    bytes += read;
                }

                byte recordType = buffer.get(JournalCodec.offRecordType());
                long ts = buffer.getLong(JournalCodec.offTs());

                if (recordType == JournalCodec.TYPE_NEW) {
                    long orderId = buffer.getLong(JournalCodec.offOrderId());
                    long traderId = buffer.getLong(JournalCodec.offTraderId());
                    Side side = JournalCodec.unmapSide(buffer.get(JournalCodec.offSide()));
                    OrderType type = JournalCodec.unmapOrderType(buffer.get(JournalCodec.offOrderType()));
                    TimeInForce tif = JournalCodec.unmapTif(buffer.get(JournalCodec.offTif()));
                    long price = buffer.getLong(JournalCodec.offPrice());
                    long stopPrice = buffer.getLong(JournalCodec.offStopPrice());
                    long quantity = buffer.getLong(JournalCodec.offQuantity());
                    long peak = buffer.getLong(JournalCodec.offPeak());

                    switch (type) {
                        case LIMIT -> engine.submitLimitAt(orderId, traderId, side, price, quantity, tif, peak, ts);
                        case MARKET -> engine.submitMarketAt(orderId, traderId, side, quantity, tif, ts);
                        case STOP_MARKET -> engine.submitStopMarketAt(orderId, traderId, side, stopPrice, quantity, ts);
                    }
                } else if (recordType == JournalCodec.TYPE_CANCEL) {
                    long cancelOrderId = buffer.getLong(JournalCodec.offCancelOrderId());
                    engine.submitCancelAt(cancelOrderId, ts);
                }
                replayed++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to replay journal: " + path, e);
        }
    }
}
