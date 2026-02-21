#pragma once

#include <cstdint>
#include <list>
#include <map>

namespace pulseengine {

struct Order {
    std::int64_t orderId;
    double price;
    std::int64_t qty;
    bool isBuy;
};

struct OrderQueue {
    std::list<Order> orders;
    std::int64_t totalQty = 0;

    void push(const Order& order) {
        orders.push_back(order);
        totalQty += order.qty;
    }
};

struct PriceLevel {
    double price = 0.0;
    OrderQueue queue;
};

struct MatchResultNative {
    std::int64_t filledQty = 0;
    std::int64_t remainingQty = 0;
    std::int32_t trades = 0;
    double avgPrice = 0.0;
    double lastTradePrice = 0.0;
};

struct L2UpdateNative {
    double bestBid = 0.0;
    std::int64_t bestBidQty = 0;
    double bestAsk = 0.0;
    std::int64_t bestAskQty = 0;
};

class OrderBook {
public:
    void insertLimitOrder(Order& order) {
        if (order.isBuy) {
            auto [it, inserted] = bids_.emplace(order.price, PriceLevel{});
            if (inserted) {
                it->second.price = order.price;
            }
            it->second.queue.push(order);
        } else {
            auto [it, inserted] = asks_.emplace(order.price, PriceLevel{});
            if (inserted) {
                it->second.price = order.price;
            }
            it->second.queue.push(order);
        }
    }

    MatchResultNative matchMarketOrder(Order& aggressor) {
        MatchResultNative result{};
        result.remainingQty = aggressor.qty;
        if (result.remainingQty <= 0) {
            return result;
        }

        double notional = 0.0;
        if (aggressor.isBuy) {
            matchAgainst(asks_, result, notional);
        } else {
            matchAgainst(bids_, result, notional);
        }

        if (result.filledQty > 0) {
            result.avgPrice = notional / static_cast<double>(result.filledQty);
        }
        return result;
    }

    L2UpdateNative publishL2Update() const {
        L2UpdateNative out{};
        if (!bids_.empty()) {
            const auto& level = bids_.begin()->second;
            out.bestBid = level.price;
            out.bestBidQty = level.queue.totalQty;
        }
        if (!asks_.empty()) {
            const auto& level = asks_.begin()->second;
            out.bestAsk = level.price;
            out.bestAskQty = level.queue.totalQty;
        }
        return out;
    }

private:
    template <typename BookMap>
    static void matchAgainst(BookMap& passiveBook, MatchResultNative& result, double& notional) {
        while (result.remainingQty > 0 && !passiveBook.empty()) {
            auto levelIt = passiveBook.begin();
            auto& queue = levelIt->second.queue;

            while (result.remainingQty > 0 && !queue.orders.empty()) {
                Order& passive = queue.orders.front();
                std::int64_t traded = (result.remainingQty < passive.qty) ? result.remainingQty : passive.qty;
                passive.qty -= traded;
                queue.totalQty -= traded;
                result.remainingQty -= traded;
                result.filledQty += traded;
                notional += static_cast<double>(traded) * passive.price;
                result.lastTradePrice = passive.price;
                result.trades += 1;

                if (passive.qty == 0) {
                    queue.orders.pop_front();
                }
            }

            if (queue.orders.empty()) {
                passiveBook.erase(levelIt);
            }
        }
    }

    std::map<double, PriceLevel, std::greater<>> bids_;
    std::map<double, PriceLevel, std::less<>> asks_;
};

} // namespace pulseengine
