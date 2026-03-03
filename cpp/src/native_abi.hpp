#pragma once

namespace pulseengine {

constexpr int NATIVE_API_VERSION = 2;
constexpr int NATIVE_MIN_COMPATIBLE_API_VERSION = 1;

constexpr int NATIVE_COMMAND_LAYOUT_VERSION = 1;
constexpr int NATIVE_COMMAND_FIXED_LENGTH = 80;
constexpr int NATIVE_COMMAND_OFF_TYPE = 0;
constexpr int NATIVE_COMMAND_OFF_SIDE = 1;
constexpr int NATIVE_COMMAND_OFF_TIF = 2;
constexpr int NATIVE_COMMAND_OFF_ORDER_ID = 8;
constexpr int NATIVE_COMMAND_OFF_TRADER_ID = 16;
constexpr int NATIVE_COMMAND_OFF_PRICE = 24;
constexpr int NATIVE_COMMAND_OFF_STOP_PRICE = 32;
constexpr int NATIVE_COMMAND_OFF_QUANTITY = 40;
constexpr int NATIVE_COMMAND_OFF_PEAK = 48;
constexpr int NATIVE_COMMAND_OFF_CANCEL_ORDER_ID = 56;

inline int nativeCommandLayoutHash() {
    int h = 17;
    h = 31 * h + NATIVE_COMMAND_LAYOUT_VERSION;
    h = 31 * h + NATIVE_COMMAND_FIXED_LENGTH;
    h = 31 * h + NATIVE_COMMAND_OFF_TYPE;
    h = 31 * h + NATIVE_COMMAND_OFF_SIDE;
    h = 31 * h + NATIVE_COMMAND_OFF_TIF;
    h = 31 * h + NATIVE_COMMAND_OFF_ORDER_ID;
    h = 31 * h + NATIVE_COMMAND_OFF_TRADER_ID;
    h = 31 * h + NATIVE_COMMAND_OFF_PRICE;
    h = 31 * h + NATIVE_COMMAND_OFF_STOP_PRICE;
    h = 31 * h + NATIVE_COMMAND_OFF_QUANTITY;
    h = 31 * h + NATIVE_COMMAND_OFF_PEAK;
    h = 31 * h + NATIVE_COMMAND_OFF_CANCEL_ORDER_ID;
    return h;
}

} // namespace pulseengine
