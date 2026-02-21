#include <benchmark/benchmark.h>

#include "native_order_book.hpp"

static void BM_InsertLimitOrder(benchmark::State& state) {
    for (auto _ : state) {
        pulseengine::OrderBook book;
        for (std::int64_t i = 1; i <= state.range(0); ++i) {
            pulseengine::Order order{
                i,
                50'000.0 + static_cast<double>(i % 64),
                10 + (i % 32),
                (i & 1) == 0
            };
            book.insertLimitOrder(order);
        }
        benchmark::DoNotOptimize(book.publishL2Update());
    }
}

BENCHMARK(BM_InsertLimitOrder)->Arg(1'000)->Arg(10'000)->Arg(100'000);

BENCHMARK_MAIN();
