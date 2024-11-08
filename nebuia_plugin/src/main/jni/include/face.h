//
// Created by Miguel Angel on 10/08/21.
//

#ifndef Face_H
#define Face_H

#include <android/asset_manager_jni.h>
#include "net.h"
#include <algorithm>
#include <iostream>
#include <string>
#include <vector>


#include <jni.h>

#define num_featuremap 4
#define hard_nms 1
#define blending_nms 2

typedef struct BoxInfo {
    float x1;
    float y1;
    float x2;
    float y2;
    float prob;
    int label;
    float area;
} BoxInfo;

class Face {
public:
    Face(AAssetManager *mgr, const char *param, const char *bin, float iou_threshold_ = 0.5, int topk_ = -1);

    ~Face();

    std::vector<BoxInfo>  detect(JNIEnv *env, jobject image);

private:
    void generateBBox(std::vector<BoxInfo> &bbox_collection, ncnn::Mat scores, ncnn::Mat boxes, float score_threshold, int num_anchors);
    void nms(std::vector<BoxInfo> &input, std::vector<BoxInfo> &output, int type = blending_nms) const;

private:
    ncnn::Net *Net;

    int image_w;
    int image_h;

    int in_w;
    int in_h;
    int num_anchors;

    int topk;
    float score_threshold;
    float iou_threshold;


    const float mean_vals[3] = {127, 127, 127};
    const float norm_vals[3] = {1.0 / 128, 1.0 / 128, 1.0 / 128};

    const float center_variance = 0.1;
    const float size_variance = 0.2;
    const std::vector<std::vector<float>> min_boxes = {
            {10.0f,  16.0f,  24.0f},
            {32.0f,  48.0f},
            {64.0f,  96.0f},
            {128.0f, 192.0f, 256.0f}};
    const std::vector<float> strides = {8.0, 16.0, 32.0, 64.0};
    std::vector<std::vector<float>> featuremap_size;
    std::vector<std::vector<float>> shrinkage_size;
    std::vector<int> w_h_list;

    std::vector<std::vector<float>> priors = {};

public:
    static Face *detector;
};

#endif //Face_H