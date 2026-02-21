#include <jni.h>

#include <cstdint>
#include <list>
#include <map>
#include <memory>
#include <stdexcept>

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
        auto& book = order.isBuy ? bids_ : asks_;
        auto [it, inserted] = book.emplace(order.price, PriceLevel{});
        if (inserted) {
            it->second.price = order.price;
        }
        it->second.queue.push(order);
    }

    MatchResultNative matchMarketOrder(Order& aggressor) {
        MatchResultNative result{};
        result.remainingQty = aggressor.qty;
        if (result.remainingQty <= 0) {
            return result;
        }

        double notional = 0.0;
        auto& passiveBook = aggressor.isBuy ? asks_ : bids_;
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
    std::map<double, PriceLevel, std::greater<>> bids_;
    std::map<double, PriceLevel, std::less<>> asks_;
};

static OrderBook* fromHandle(jlong handle) {
    if (handle == 0) {
        throw std::invalid_argument("native handle is null");
    }
    return reinterpret_cast<OrderBook*>(handle);
}

static jclass loadClass(JNIEnv* env, const char* binaryName) {
    jclass cls = env->FindClass(binaryName);
    if (cls == nullptr) {
        throw std::runtime_error("JNI class lookup failed");
    }
    return cls;
}

} // namespace pulseengine

extern "C" {

JNIEXPORT jlong JNICALL Java_io_pulseengine_jni_NativeMatchingEngine_nativeCreate(JNIEnv*, jclass) {
    auto* engine = new pulseengine::OrderBook();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL Java_io_pulseengine_jni_NativeMatchingEngine_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    auto* engine = reinterpret_cast<pulseengine::OrderBook*>(handle);
    delete engine;
}

JNIEXPORT void JNICALL Java_io_pulseengine_jni_NativeMatchingEngine_nativeInsertLimitOrder(
    JNIEnv* env,
    jclass,
    jlong handle,
    jlong orderId,
    jdouble price,
    jlong qty,
    jboolean isBuy
) {
    try {
        pulseengine::Order order{orderId, price, qty, isBuy == JNI_TRUE};
        pulseengine::fromHandle(handle)->insertLimitOrder(order);
    } catch (const std::exception& ex) {
        jclass rte = env->FindClass("java/lang/RuntimeException");
        if (rte != nullptr) {
            env->ThrowNew(rte, ex.what());
        }
    }
}

JNIEXPORT jobject JNICALL Java_io_pulseengine_jni_NativeMatchingEngine_nativeMatchMarketOrder(
    JNIEnv* env,
    jclass,
    jlong handle,
    jlong orderId,
    jlong qty,
    jboolean isBuy
) {
    try {
        pulseengine::Order order{orderId, 0.0, qty, isBuy == JNI_TRUE};
        pulseengine::MatchResultNative result = pulseengine::fromHandle(handle)->matchMarketOrder(order);

        jclass resultClass = pulseengine::loadClass(env, "io/pulseengine/jni/NativeMatchingEngine$MatchResult");
        jmethodID ctor = env->GetMethodID(resultClass, "<init>", "(JJIDD)V");
        return env->NewObject(
            resultClass,
            ctor,
            static_cast<jlong>(result.filledQty),
            static_cast<jlong>(result.remainingQty),
            static_cast<jint>(result.trades),
            static_cast<jdouble>(result.avgPrice),
            static_cast<jdouble>(result.lastTradePrice)
        );
    } catch (const std::exception& ex) {
        jclass rte = env->FindClass("java/lang/RuntimeException");
        if (rte != nullptr) {
            env->ThrowNew(rte, ex.what());
        }
        return nullptr;
    }
}

JNIEXPORT jobject JNICALL Java_io_pulseengine_jni_NativeMatchingEngine_nativePublishL2Update(
    JNIEnv* env,
    jclass,
    jlong handle
) {
    try {
        pulseengine::L2UpdateNative update = pulseengine::fromHandle(handle)->publishL2Update();

        jclass updateClass = pulseengine::loadClass(env, "io/pulseengine/jni/NativeMatchingEngine$L2Update");
        jmethodID ctor = env->GetMethodID(updateClass, "<init>", "(DJDJ)V");
        return env->NewObject(
            updateClass,
            ctor,
            static_cast<jdouble>(update.bestBid),
            static_cast<jlong>(update.bestBidQty),
            static_cast<jdouble>(update.bestAsk),
            static_cast<jlong>(update.bestAskQty)
        );
    } catch (const std::exception& ex) {
        jclass rte = env->FindClass("java/lang/RuntimeException");
        if (rte != nullptr) {
            env->ThrowNew(rte, ex.what());
        }
        return nullptr;
    }
}

} // extern "C"
