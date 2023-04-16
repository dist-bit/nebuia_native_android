//
// Created by miguel on 01/11/21.
//

#ifndef NEBUIA_QUALITY_H
#define NEBUIA_QUALITY_H

#include <android/asset_manager_jni.h>
#include "net.h"
#include <android/bitmap.h>
#include <jni.h>
#include <opencv2/core/mat.hpp>

class Quality {
public:
    Quality(AAssetManager *mgr, const char *param, const char *bin);
    ~Quality();
    float quality(JNIEnv *env, jobject bitmap) const;

private:
    ncnn::Net *Net;
    int target_size = 224;
    const float mean_values[3] = {123.675f, 116.28f, 103.53};
    const float norm_values[3] = {1.0f / 58.395f, 1.0f / 57.12f, 1.0f / 57.375f};

    ncnn::UnlockedPoolAllocator blob_pool_allocator;
    ncnn::PoolAllocator workspace_pool_allocator;
};

#endif //NEBUIA_QUALITY_H
