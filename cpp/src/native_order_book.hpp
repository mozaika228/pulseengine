#pragma once

#include <cstdint>
#include <vector>

namespace pulseengine {

struct Order {
    std::int64_t orderId;
    double price;
    std::int64_t qty;
    bool isBuy;
};

struct OrderQueue {
    int head = -1;
    int tail = -1;
    std::int64_t totalQty = 0;

    [[nodiscard]] bool empty() const {
        return head < 0;
    }
};

struct PriceLevel {
    double price = 0.0;
    OrderQueue queue{};
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
    explicit OrderBook(std::size_t maxLevels = 1024, std::size_t maxOrders = 16384)
        : maxLevels_(static_cast<int>(maxLevels)),
          bids_(maxLevels),
          asks_(maxLevels),
          orderPool_(maxOrders) {
    }

    void insertLimitOrder(Order& order) {
        if (order.qty <= 0) {
            return;
        }

        std::vector<PriceLevel>& levels = order.isBuy ? bids_ : asks_;
        int& levelCount = order.isBuy ? bidCount_ : askCount_;
        int pos = findLevelInsertPos(levels, levelCount, order.price, order.isBuy);

        if (pos < levelCount && levels[static_cast<std::size_t>(pos)].price == order.price) {
            enqueue(levels[static_cast<std::size_t>(pos)].queue, order.orderId, order.qty);
            return;
        }

        if (levelCount >= maxLevels_) {
            return;
        }

        shiftRight(levels, levelCount, pos);
        PriceLevel& level = levels[static_cast<std::size_t>(pos)];
        level.price = order.price;
        level.queue = OrderQueue{};
        enqueue(level.queue, order.orderId, order.qty);
        levelCount++;
    }

    MatchResultNative matchMarketOrder(Order& aggressor) {
        MatchResultNative result{};
        result.remainingQty = aggressor.qty;
        if (result.remainingQty <= 0) {
            return result;
        }

        double notional = 0.0;
        std::vector<PriceLevel>& passiveLevels = aggressor.isBuy ? asks_ : bids_;
        int& passiveCount = aggressor.isBuy ? askCount_ : bidCount_;

        while (result.remainingQty > 0 && passiveCount > 0) {
            PriceLevel& level = passiveLevels[0];
            OrderQueue& q = level.queue;

            while (result.remainingQty > 0 && !q.empty()) {
                int nodeIdx = q.head;
                Node& node = orderPool_.at(nodeIdx);

                const std::int64_t traded = (result.remainingQty < node.qty) ? result.remainingQty : node.qty;
                node.qty -= traded;
                q.totalQty -= traded;
                result.remainingQty -= traded;
                result.filledQty += traded;
                result.trades += 1;
                result.lastTradePrice = level.price;
                notional += static_cast<double>(traded) * level.price;

                if (node.qty == 0) {
                    q.head = node.next;
                    if (q.head < 0) {
                        q.tail = -1;
                    }
                    orderPool_.release(nodeIdx);
                }
            }

            if (q.empty()) {
                shiftLeft(passiveLevels, passiveCount, 0);
                passiveCount--;
            }
        }

        if (result.filledQty > 0) {
            result.avgPrice = notional / static_cast<double>(result.filledQty);
        }
        return result;
    }

    [[nodiscard]] L2UpdateNative publishL2Update() const {
        L2UpdateNative out{};
        if (bidCount_ > 0) {
            out.bestBid = bids_[0].price;
            out.bestBidQty = bids_[0].queue.totalQty;
        }
        if (askCount_ > 0) {
            out.bestAsk = asks_[0].price;
            out.bestAskQty = asks_[0].queue.totalQty;
        }
        return out;
    }

private:
    struct Node {
        std::int64_t orderId = 0;
        std::int64_t qty = 0;
        int next = -1;
    };

    class NodePool {
    public:
        explicit NodePool(std::size_t maxNodes)
            : nodes_(maxNodes),
              freeList_(maxNodes),
              freeTop_(static_cast<int>(maxNodes)) {
            for (int i = 0; i < freeTop_; i++) {
                freeList_[static_cast<std::size_t>(i)] = freeTop_ - 1 - i;
            }
        }

        int acquire(std::int64_t orderId, std::int64_t qty) {
            if (freeTop_ == 0) {
                return -1;
            }
            freeTop_--;
            int idx = freeList_[static_cast<std::size_t>(freeTop_)];
            Node& n = nodes_[static_cast<std::size_t>(idx)];
            n.orderId = orderId;
            n.qty = qty;
            n.next = -1;
            return idx;
        }

        void release(int idx) {
            Node& n = nodes_[static_cast<std::size_t>(idx)];
            n.orderId = 0;
            n.qty = 0;
            n.next = -1;
            freeList_[static_cast<std::size_t>(freeTop_)] = idx;
            freeTop_++;
        }

        Node& at(int idx) {
            return nodes_[static_cast<std::size_t>(idx)];
        }

    private:
        std::vector<Node> nodes_;
        std::vector<int> freeList_;
        int freeTop_;
    };

    void enqueue(OrderQueue& queue, std::int64_t orderId, std::int64_t qty) {
        int idx = orderPool_.acquire(orderId, qty);
        if (idx < 0) {
            return;
        }
        if (queue.tail >= 0) {
            orderPool_.at(queue.tail).next = idx;
        } else {
            queue.head = idx;
        }
        queue.tail = idx;
        queue.totalQty += qty;
    }

    static int findLevelInsertPos(const std::vector<PriceLevel>& levels, int count, double price, bool isBuy) {
        if (count == 0) {
            return 0;
        }
        int lo = 0;
        int hi = count;
        while (lo < hi) {
            int mid = lo + ((hi - lo) / 2);
            double midPrice = levels[static_cast<std::size_t>(mid)].price;
            if (midPrice == price) {
                return mid;
            }
            if (isBuy) {
                if (midPrice < price) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            } else {
                if (midPrice > price) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            }
        }
        return lo;
    }

    static void shiftRight(std::vector<PriceLevel>& levels, int count, int from) {
        for (int i = count; i > from; i--) {
            levels[static_cast<std::size_t>(i)] = levels[static_cast<std::size_t>(i - 1)];
        }
    }

    static void shiftLeft(std::vector<PriceLevel>& levels, int count, int from) {
        for (int i = from; i < count - 1; i++) {
            levels[static_cast<std::size_t>(i)] = levels[static_cast<std::size_t>(i + 1)];
        }
        levels[static_cast<std::size_t>(count - 1)] = PriceLevel{};
    }

    int maxLevels_;
    int bidCount_ = 0;
    int askCount_ = 0;
    std::vector<PriceLevel> bids_;
    std::vector<PriceLevel> asks_;
    NodePool orderPool_;
};

} // namespace pulseengine
