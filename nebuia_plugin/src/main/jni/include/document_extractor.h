//
// Created by miguel on 01/11/21.
//

#ifndef NEBUIA_DOCUMENT_H
#define NEBUIA_DOCUMENT_H

#include <android/asset_manager_jni.h>
#include "net.h"
#include <opencv2/core/core.hpp>
#include <jni.h>
#include <android/log.h>

struct Landmarks
{
    float x;
    float y;
};

struct Doc
{
    cv::Rect_<float> rect;
    int label;
    long prob;
    std::vector<Landmarks> pts;
};


class DocumentExtractor {
public:
    DocumentExtractor(AAssetManager *mgr, const char *param, const char *bin);

    ~DocumentExtractor();

    std::vector<Doc> detect(JNIEnv *env, jobject bitmap, std::vector<Doc>& objects);

private:
    ncnn::Net *Net;

    int target_size = 384;
    const float norm_vals[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};

    ncnn::UnlockedPoolAllocator blob_pool_allocator;
    ncnn::PoolAllocator workspace_pool_allocator;

    float prob_threshold = 0.35f;
    float nms_threshold = 0.55f;
};

#endif //NEBUIA_DOCUMENT_H
