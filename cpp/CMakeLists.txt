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
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)

if (MSVC)
    target_compile_options(pulseengine_native PRIVATE /O2 /EHsc /W4)
else()
    target_compile_options(pulseengine_native PRIVATE -O3 -Wall -Wextra -Wpedantic)
endif()

option(PULSEENGINE_BUILD_BENCHMARKS "Build C++ Google Benchmark targets" ON)
if (PULSEENGINE_BUILD_BENCHMARKS)
    find_package(benchmark QUIET)
    if (benchmark_FOUND)
        add_executable(order_book_insert_bench
            bench/order_book_insert_bench.cpp
        )
        target_include_directories(order_book_insert_bench PRIVATE
            ${CMAKE_CURRENT_SOURCE_DIR}/src
        )
        target_link_libraries(order_book_insert_bench PRIVATE benchmark::benchmark)
        if (MSVC)
            target_compile_options(order_book_insert_bench PRIVATE /O2 /EHsc /W4)
        else()
            target_compile_options(order_book_insert_bench PRIVATE -O3 -Wall -Wextra -Wpedantic)
        endif()
    else()
        message(WARNING "Google Benchmark not found; order_book_insert_bench target will not be built")
    endif()
endif()
