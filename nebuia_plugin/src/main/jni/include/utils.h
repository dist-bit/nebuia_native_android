//
// Created by miguel on 12/09/22.
//

#ifndef NEBUIAEXAMPLE_UTILS_H
#define NEBUIAEXAMPLE_UTILS_H

#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <mat.h>
#include "opencv2/opencv.hpp"

class Utils {
public:
    static void processCLAHE(JNIEnv * env, jobject in, jobject out);
    static cv::Mat transformMat(JNIEnv *env, jobject process);
    static void rotate(const cv::Mat in, float angle);
    static cv::Mat resize(const cv::Mat& in, int w, int h);
};
#endif //NEBUIAEXAMPLE_UTILS_H
