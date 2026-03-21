cmake_minimum_required(VERSION 3.22.1)

project("audio_processor")

# Shared library compiled from the native DSP source
add_library(
    audio_processor
    SHARED
    audio_processor.cpp
)

# Link Android logging and math libraries
target_link_libraries(
    audio_processor
    android
    log
    m
)