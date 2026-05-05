cmake_minimum_required(VERSION 3.22.1)
project("notificationarchiver")

add_library(
        notificationarchiver
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        notificationarchiver
        ${log-lib})