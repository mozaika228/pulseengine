#pragma once

#include <algorithm>
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
    explicit OrderBook(std::size_t reserveLevels = 1024, std::size_t reserveOrders = 16384)
        : orderPool_(reserveOrders) {
        bids_.reserve(reserveLevels);
        asks_.reserve(reserveLevels);
    }

    void insertLimitOrder(Order& order) {
        if (order.qty <= 0) {
            return;
        }

        auto& levels = order.isBuy ? bids_ : asks_;
        int pos = findLevelInsertPos(levels, order.price, order.isBuy);
        if (pos < static_cast<int>(levels.size()) && levels[static_cast<std::size_t>(pos)].price == order.price) {
            enqueue(levels[static_cast<std::size_t>(pos)].queue, order.orderId, order.qty);
            return;
        }

        PriceLevel level{};
        level.price = order.price;
        enqueue(level.queue, order.orderId, order.qty);
        levels.insert(levels.begin() + pos, level);
    }

    MatchResultNative matchMarketOrder(Order& aggressor) {
        MatchResultNative result{};
        result.remainingQty = aggressor.qty;
        if (result.remainingQty <= 0) {
            return result;
        }

        double notional = 0.0;
        auto& passiveLevels = aggressor.isBuy ? asks_ : bids_;
        while (result.remainingQty > 0 && !passiveLevels.empty()) {
            PriceLevel& level = passiveLevels.front();
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
                passiveLevels.erase(passiveLevels.begin());
            }
        }

        if (result.filledQty > 0) {
            result.avgPrice = notional / static_cast<double>(result.filledQty);
        }
        return result;
    }

    [[nodiscard]] L2UpdateNative publishL2Update() const {
        L2UpdateNative out{};
        if (!bids_.empty()) {
            out.bestBid = bids_.front().price;
            out.bestBidQty = bids_.front().queue.totalQty;
        }
        if (!asks_.empty()) {
            out.bestAsk = asks_.front().price;
            out.bestAskQty = asks_.front().queue.totalQty;
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
        explicit NodePool(std::size_t reserveNodes) {
            nodes_.reserve(reserveNodes);
            freeList_.reserve(reserveNodes / 2);
        }

        int acquire(std::int64_t orderId, std::int64_t qty) {
            int idx;
            if (!freeList_.empty()) {
                idx = freeList_.back();
                freeList_.pop_back();
                Node& n = nodes_[static_cast<std::size_t>(idx)];
                n.orderId = orderId;
                n.qty = qty;
                n.next = -1;
            } else {
                idx = static_cast<int>(nodes_.size());
                nodes_.push_back(Node{orderId, qty, -1});
            }
            return idx;
        }

        void release(int idx) {
            Node& n = nodes_[static_cast<std::size_t>(idx)];
            n.orderId = 0;
            n.qty = 0;
            n.next = -1;
            freeList_.push_back(idx);
        }

        Node& at(int idx) {
            return nodes_[static_cast<std::size_t>(idx)];
        }

    private:
        std::vector<Node> nodes_;
        std::vector<int> freeList_;
    };

    void enqueue(OrderQueue& queue, std::int64_t orderId, std::int64_t qty) {
        int idx = orderPool_.acquire(orderId, qty);
        if (queue.tail >= 0) {
            orderPool_.at(queue.tail).next = idx;
        } else {
            queue.head = idx;
        }
        queue.tail = idx;
        queue.totalQty += qty;
    }

    static int findLevelInsertPos(const std::vector<PriceLevel>& levels, double price, bool isBuy) {
        if (levels.empty()) {
            return 0;
        }
        int lo = 0;
        int hi = static_cast<int>(levels.size());
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

    std::vector<PriceLevel> bids_;
    std::vector<PriceLevel> asks_;
    NodePool orderPool_;
};

} // namespace pulseengine
