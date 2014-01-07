LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := ffmpeg
LOCAL_SRC_FILES := ffmpeg.cpp

include $(BUILD_SHARED_LIBRARY)
