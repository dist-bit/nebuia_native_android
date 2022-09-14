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
    float qualityMat(const cv::Mat& image) const;

private:
    ncnn::Net *Net;
    int target_size = 320;
    const float mean_values[3] = {127.5f, 127.5f, 127.5f};
    const float norm_values[3] = {1.0 / 127.5, 1.0 / 127.5, 1.0 / 127.5};
    ncnn::UnlockedPoolAllocator blob_pool_allocator;
    ncnn::PoolAllocator workspace_pool_allocator;
};

#endif //NEBUIA_QUALITY_H
