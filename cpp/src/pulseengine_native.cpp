#include <jni.h>

#include <stdexcept>

#include "native_abi.hpp"
#include "native_order_book.hpp"

namespace pulseengine {

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

static void throwRuntime(JNIEnv* env, const char* message) {
    jclass rte = env->FindClass("java/lang/RuntimeException");
    if (rte != nullptr) {
        env->ThrowNew(rte, message);
    }
}

} // namespace pulseengine

extern "C" {

JNIEXPORT jlong JNICALL Java_io_pulseengine_jni_NativeOrderBook_nativeCreate(JNIEnv*, jclass) {
    auto* engine = new pulseengine::OrderBook();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT jlong JNICALL Java_io_pulseengine_jni_NativeOrderBook_nativeCreateWithCapacity(
    JNIEnv* env,
    jclass,
    jint maxLevels,
    jint maxOrders
) {
    try {
        if (maxLevels <= 0 || maxOrders <= 0) {
            throw std::invalid_argument("maxLevels/maxOrders must be positive");
        }
        auto* engine = new pulseengine::OrderBook(static_cast<std::size_t>(maxLevels), static_cast<std::size_t>(maxOrders));
        return reinterpret_cast<jlong>(engine);
    } catch (const std::exception& ex) {
        pulseengine::throwRuntime(env, ex.what());
        return 0;
    }
}

JNIEXPORT void JNICALL Java_io_pulseengine_jni_NativeOrderBook_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    auto* engine = reinterpret_cast<pulseengine::OrderBook*>(handle);
    delete engine;
}

JNIEXPORT jint JNICALL Java_io_pulseengine_jni_NativeOrderBook_nativeInsertLimitOrder(
    JNIEnv* env,
    jclass,
    jlong handle,
    jlong orderId,
    jdouble price,
    jlong qty,
    jboolean isBuy
) {
    try {
        pulseengine::Order order{orderId, price, qty, 0, isBuy == JNI_TRUE};
        pulseengine::InsertStatusNative status = pulseengine::fromHandle(handle)->insertLimitOrder(order);
        return static_cast<jint>(status);
    } catch (const std::exception& ex) {
        pulseengine::throwRuntime(env, ex.what());
        return -1;
    }
}

JNIEXPORT jint JNICALL Java_io_pulseengine_jni_NativeOrderBook_nativeInsertLimitIceberg(
    JNIEnv* env,
    jclass,
    jlong handle,
    jlong orderId,
    jdouble price,
    jlong qty,
    jlong peakQty,
    jboolean isBuy
) {
    try {
        pulseengine::Order order{orderId, price, qty, peakQty, isBuy == JNI_TRUE};
        pulseengine::InsertStatusNative status = pulseengine::fromHandle(handle)->insertLimitOrder(order);
        return static_cast<jint>(status);
    } catch (const std::exception& ex) {
        pulseengine::throwRuntime(env, ex.what());
        return -1;
    }
}

JNIEXPORT jobject JNICALL Java_io_pulseengine_jni_NativeOrderBook_nativeMatchMarketOrder(
    JNIEnv* env,
    jclass,
    jlong handle,
    jlong orderId,
    jlong qty,
    jboolean isBuy
) {
    try {
        pulseengine::Order order{orderId, 0.0, qty, 0, isBuy == JNI_TRUE};
        pulseengine::MatchResultNative result = pulseengine::fromHandle(handle)->matchMarketOrder(order);

        jclass resultClass = pulseengine::loadClass(env, "io/pulseengine/jni/NativeOrderBook$MatchResult");
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
        pulseengine::throwRuntime(env, ex.what());
        return nullptr;
    }
}

JNIEXPORT jobject JNICALL Java_io_pulseengine_jni_NativeOrderBook_nativePublishL2Update(
    JNIEnv* env,
    jclass,
    jlong handle
) {
    try {
        pulseengine::L2UpdateNative update = pulseengine::fromHandle(handle)->publishL2Update();

        jclass updateClass = pulseengine::loadClass(env, "io/pulseengine/jni/NativeOrderBook$L2Update");
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
        pulseengine::throwRuntime(env, ex.what());
        return nullptr;
    }
}

JNIEXPORT jint JNICALL Java_io_pulseengine_jni_NativeOrderBook_nativeApiVersion(JNIEnv*, jclass) {
    return static_cast<jint>(pulseengine::NATIVE_API_VERSION);
}

JNIEXPORT jint JNICALL Java_io_pulseengine_jni_NativeOrderBook_nativeMinCompatibleApiVersion(JNIEnv*, jclass) {
    return static_cast<jint>(pulseengine::NATIVE_MIN_COMPATIBLE_API_VERSION);
}

JNIEXPORT jint JNICALL Java_io_pulseengine_jni_NativeOrderBook_nativeCommandLayoutVersion(JNIEnv*, jclass) {
    return static_cast<jint>(pulseengine::NATIVE_COMMAND_LAYOUT_VERSION);
}

JNIEXPORT jint JNICALL Java_io_pulseengine_jni_NativeOrderBook_nativeCommandLayoutHash(JNIEnv*, jclass) {
    return static_cast<jint>(pulseengine::nativeCommandLayoutHash());
}

} // extern "C"