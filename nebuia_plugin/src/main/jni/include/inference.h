//
// Created by miguel on 01/11/21.
//

#ifndef NEBUIA_INFERENCE_H
#define NEBUIA_INFERENCE_H

#include <android/asset_manager_jni.h>
#include "net.h"

#include <jni.h>

struct Object
{
    float x;
    float y;
    float w;
    float h;
    int label;
    float prob;
};


class Inference {
public:
    Inference(AAssetManager *mgr, const char *param, const char *bin);

    ~Inference();

    std::vector<Object> detect(JNIEnv *env, jobject image, int items) const;

private:
    ncnn::Net *Net;
    const float mean_values[3] = {103.53f, 116.28f, 123.675f};
    const float norm_values[3] = {0.017429f, 0.017507f, 0.017125f};
    int target_size = 320;
    ncnn::UnlockedPoolAllocator blob_pool_allocator;
    ncnn::PoolAllocator workspace_pool_allocator;
};

#endif //NEBUIA_INFERENCE_H
