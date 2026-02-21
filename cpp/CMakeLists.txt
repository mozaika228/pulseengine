cmake_minimum_required(VERSION 3.20)
project(pulseengine_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(JNI REQUIRED)

add_library(pulseengine_native SHARED
    src/pulseengine_native.cpp
)

target_include_directories(pulseengine_native PRIVATE
    ${JNI_INCLUDE_DIRS}
)

if (MSVC)
    target_compile_options(pulseengine_native PRIVATE /O2 /EHsc /W4)
else()
    target_compile_options(pulseengine_native PRIVATE -O3 -Wall -Wextra -Wpedantic)
endif()
