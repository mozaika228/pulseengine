package io.pulseengine.app;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import io.pulseengine.core.MatchEventSink;
import io.pulseengine.core.Side;
import io.pulseengine.core.SmpPolicy;
import io.pulseengine.core.TimeInForce;
import io.pulseengine.md.BinaryMdPublisher;
import io.pulseengine.md.TopOfBookView;
import io.pulseengine.pipeline.EnginePipeline;
import io.pulseengine.transport.aeron.AeronChannels;
import io.pulseengine.transport.aeron.AeronMdSink;
import io.pulseengine.transport.aeron.AeronOrderGateway;
import io.pulseengine.transport.aeron.AeronOrderIngress;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class AeronUdpMulticastDemo {
    private AeronUdpMulticastDemo() {
    }

    public static void main(String[] args) throws InterruptedException {
        long connectTimeoutSec = args.length > 0 ? Long.parseLong(args[0]) : 5L;
        boolean allowIpcFallback = args.length > 1 && Boolean.parseBoolean(args[1]);

        try {
            runDemo(
                AeronChannels.UDP_INGRESS_UNICAST,
                AeronChannels.UDP_MD_MULTICAST,
                "udp_unicast+multicast",
                connectTimeoutSec
            );
        } catch (IllegalStateException e) {
            if (!allowIpcFallback) {
                throw e;
            }
            System.out.println("aeron_transport_fallback=ipc");
            System.out.println("aeron_transport_fallback_reason=" + e.getMessage().replace(' ', '_'));
            runDemo(
                AeronChannels.IPC_CHANNEL,
                AeronChannels.IPC_CHANNEL,
                "ipc_fallback",
                Math.max(3L, connectTimeoutSec)
            );
        }
    }

    private static void runDemo(String ingressChannel, String mdChannel, String transportLabel, long connectTimeoutSec)
        throws InterruptedException {

        MediaDriver.Context mdCtx = new MediaDriver.Context()
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true)
            .spiesSimulateConnection(true);

        try (MediaDriver driver = MediaDriver.launchEmbedded(mdCtx)) {
            Aeron.Context aeronCtx = new Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName());
            try (Aeron aeron = Aeron.connect(aeronCtx);
                 AeronMdSink aeronMdSink = new AeronMdSink(aeron, mdChannel, AeronChannels.MD_STREAM_ID)) {

                TopOfBookView topOfBook = new TopOfBookView();
                BinaryMdPublisher mdPublisher = new BinaryMdPublisher(aeronMdSink);
                MatchEventSink sink = new BlackholeSink();

                try (EnginePipeline engine = new EnginePipeline(
                    1 << 15,
                    sink,
                    SmpPolicy.CANCEL_AGGRESSOR,
                    topOfBook,
                    mdPublisher,
                    runnable -> {
                        Thread t = new Thread(runnable, "pulse-aeron-udp-demo");
                        t.setDaemon(true);
                        return t;
                    }
                );
                     AeronOrderGateway gateway = new AeronOrderGateway(aeron, ingressChannel, AeronChannels.ORDERS_STREAM_ID);
                     Subscription orderSub = aeron.addSubscription(ingressChannel, AeronChannels.ORDERS_STREAM_ID);
                     Subscription mdSub = aeron.addSubscription(mdChannel, AeronChannels.MD_STREAM_ID)) {

                    AeronOrderIngress ingress = new AeronOrderIngress(orderSub, engine);
                    AtomicBoolean running = new AtomicBoolean(true);
                    AtomicLong mdFragments = new AtomicLong();

                    FragmentHandler mdHandler = (buffer, offset, length, header) -> mdFragments.incrementAndGet();

                    Thread ingressThread = new Thread(() -> {
                        while (running.get()) {
                            int work = ingress.poll(64);
                            if (work == 0) {
                                Thread.onSpinWait();
                            }
                        }
                    }, "aeron-order-ingress-udp");
                    ingressThread.setDaemon(true);
                    ingressThread.start();

                    long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(connectTimeoutSec);
                    while (!(gateway.isConnected() && orderSub.imageCount() > 0 && aeronMdSink.isConnected() && mdSub.imageCount() > 0)) {
                        if (System.nanoTime() > waitDeadline) {
                            throw new IllegalStateException("Aeron channels did not connect in time");
                        }
                        mdSub.poll(mdHandler, 16);
                        Thread.onSpinWait();
                    }

                    long startSeq = topOfBook.sequence();
                    gateway.submitLimit(1, 10, Side.BUY, 49_900, 50, TimeInForce.GTC, 0);
                    gateway.submitLimit(2, 11, Side.SELL, 50_100, 40, TimeInForce.GTC, 0);
                    gateway.submitLimit(3, 12, Side.BUY, 50_200, 2, TimeInForce.IOC, 0);
                    gateway.submitLimit(4, 13, Side.SELL, 49_800, 1, TimeInForce.IOC, 0);

                    long target = startSeq + 4;
                    while (topOfBook.sequence() < target) {
                        mdSub.poll(mdHandler, 64);
                        Thread.onSpinWait();
                    }

                    long mdDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    while (mdFragments.get() == 0 && System.nanoTime() < mdDeadline) {
                        int fragments = mdSub.poll(mdHandler, 64);
                        if (fragments == 0) {
                            Thread.onSpinWait();
                        }
                    }

                    for (int i = 0; i < 64; i++) {
                        mdSub.poll(mdHandler, 64);
                    }

                    running.set(false);
                    ingressThread.join(1000);

                    System.out.println("aeron_transport=" + transportLabel);
                    System.out.println("aeron_orders_processed=" + (topOfBook.sequence() - startSeq));
                    System.out.println("aeron_md_fragments=" + mdFragments.get());
                    System.out.println("best_bid=" + topOfBook.bestBid());
                    System.out.println("best_ask=" + topOfBook.bestAsk());
                }
            }
        }
    }

    private static final class BlackholeSink implements MatchEventSink {
        private long consumed;

        @Override
        public void onTrade(long tradeId, long buyOrderId, long sellOrderId, long priceTicks, long quantity, long tsNanos) {
            consumed ^= tradeId ^ buyOrderId ^ sellOrderId ^ priceTicks ^ quantity ^ tsNanos;
        }

        @Override
        public void onOrderAccepted(long orderId, long openQty, long tsNanos) {
            consumed ^= orderId ^ openQty ^ tsNanos;
        }

        @Override
        public void onOrderRejected(long orderId, byte reasonCode, long tsNanos) {
            consumed ^= orderId ^ tsNanos ^ reasonCode;
        }

        @Override
        public void onOrderCanceled(long orderId, long remainingQty, long tsNanos) {
            consumed ^= orderId ^ remainingQty ^ tsNanos;
        }

        @Override
        public void onOrderFilled(long orderId, long tsNanos) {
            consumed ^= orderId ^ tsNanos;
        }

        @Override
        public void onOrderPartiallyFilled(long orderId, long remainingQty, long tsNanos) {
            consumed ^= orderId ^ remainingQty ^ tsNanos;
        }
    }
}

