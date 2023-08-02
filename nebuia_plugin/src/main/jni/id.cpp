//
// Created by Miguel Angel on 24/07/21.
//

#include <string>
#include <vector>
#include <utils.h>
#include "id.h"

Inference *Id::inference = nullptr;
DocumentExtractor *Id::extractor = nullptr;

extern "C" {
/*
// FIXME DeleteGlobalRef is missing for objCls
static jclass objCls = nullptr;
static jmethodID constructorId;
static jfieldID xId;
static jfieldID yId;
static jfieldID wId;
static jfieldID hId;
static jfieldID labelId;
static jfieldID probId; */

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
}

JNIEXPORT jboolean JNICALL
Java_com_distbit_nebuia_1plugin_core_Id_Init(JNIEnv *env, jobject, jobject assetManager) {
    AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);

   if (Id::inference != nullptr) {
        delete Id::inference;
        Id::inference = nullptr;
    }

    if (Id::inference == nullptr) {
        Id::inference = new Inference(mgr, "det0.param", "det0.bin");
    }

    if (Id::extractor != nullptr) {
        delete Id::extractor;
        Id::extractor = nullptr;
    }

    if (Id::extractor == nullptr) {
        Id::extractor = new DocumentExtractor(mgr, "det5.param", "det5.bin");
    }
/*
    // init jni glue
    jclass localObjCls = env->FindClass("com/distbit/nebuia_plugin/core/Id$Obj");
    objCls = reinterpret_cast<jclass>(env->NewGlobalRef(localObjCls));

    constructorId = env->GetMethodID(objCls, "<init>", "(Lcom/distbit/nebuia_plugin/core/Id;)V");

    xId = env->GetFieldID(objCls, "x", "F");
    yId = env->GetFieldID(objCls, "y", "F");
    wId = env->GetFieldID(objCls, "w", "F");
    hId = env->GetFieldID(objCls, "h", "F");
    labelId = env->GetFieldID(objCls, "label", "Ljava/lang/String;");
    probId = env->GetFieldID(objCls, "prob", "F"); */

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_distbit_nebuia_1plugin_core_Id_Detect(JNIEnv *env, jobject thiz, jobject bitmap,
                                               jobject out) {

    std::vector<Doc> items;
    Id::extractor->detect(env, bitmap, items);

    if (!items.empty()) {
        cv::Mat input = Utils::transformMat(env, bitmap);

        cv::Point2f Points[4] = {
                cv::Point2f(items[0].pts[3].x, items[0].pts[3].y),
                cv::Point2f(items[0].pts[2].x, items[0].pts[2].y),
                cv::Point2f(items[0].pts[0].x, items[0].pts[0].y),
                cv::Point2f(items[0].pts[1].x, items[0].pts[1].y),

        };

        cv::Point2f dst_vertices[4];
        dst_vertices[0] = cv::Point(0, 0);
        dst_vertices[1] = cv::Point(items[0].rect.width, 0); // 590
        dst_vertices[2] = cv::Point(0, items[0].rect.height); //389
        dst_vertices[3] = cv::Point(items[0].rect.width, items[0].rect.height);

        cv::Mat warpAffineMatrix = cv::getPerspectiveTransform(Points, dst_vertices);

        cv::Mat rotated;
        cv::Size size(items[0].rect.width, items[0].rect.height);
        cv::warpPerspective(input, rotated, warpAffineMatrix, size);

        cv::Mat resized;
        cv::resize(rotated, resized, cv::Size(590, 389));

        ncnn::Mat in2 = ncnn::Mat::from_pixels(resized.data, ncnn::Mat::PIXEL_RGB, resized.cols,
                                               resized.rows);
        in2.to_android_bitmap(env, out, ncnn::Mat::PIXEL_RGB);
        in2.release();
        resized.release();
        rotated.release();

        return JNI_TRUE;
    }

    return JNI_FALSE;

/*
    auto objects = Id::inference->detect(env, bitmap, 3);
    static const char *class_names[] = {
            "mx_id_back", "mx_id_front", "mx_passport_front"};

    if (!objects.empty()) {
        std::vector<Doc> items;
        Id::extractor->detect(env, bitmap, items);

        cv::Mat input = Utils::transformMat(env, bitmap);

        cv::Point2f Points[4] = {
                cv::Point2f(items[0].pts[0].x, items[0].pts[0].y),
                cv::Point2f(items[0].pts[1].x, items[0].pts[1].y),
                cv::Point2f(items[0].pts[2].x, items[0].pts[2].y),
                cv::Point2f(items[0].pts[3].x, items[0].pts[3].y),

        };

        cv::Point2f dst_vertices[4];
        dst_vertices[0] = cv::Point(0, 0);
        dst_vertices[1] = cv::Point(items[0].rect.width, 0); // 590
        dst_vertices[2] = cv::Point(0, items[0].rect.height); //389
        dst_vertices[3] = cv::Point(items[0].rect.width, items[0].rect.height);

        cv::Mat warpAffineMatrix = cv::getPerspectiveTransform(Points, dst_vertices);

        cv::Mat rotated;
        cv::Size size(items[0].rect.width, items[0].rect.height);
        cv::warpPerspective(input, rotated, warpAffineMatrix, size);

        cv::Mat resized;
        cv::resize(rotated, resized, cv::Size(590, 389));

        ncnn::Mat in2 = ncnn::Mat::from_pixels(resized.data, ncnn::Mat::PIXEL_RGB, resized.cols, resized.rows);
        in2.to_android_bitmap(env, out, ncnn::Mat::PIXEL_RGB);
        in2.release();
        resized.release();
        rotated.release();
    }

    jobjectArray jObjArray = env->NewObjectArray(objects.size(), objCls, nullptr);

    for (size_t i = 0; i < objects.size(); i++) {
        jobject jObj = env->NewObject(objCls, constructorId, thiz);

        env->SetFloatField(jObj, xId, objects[i].x);
        env->SetFloatField(jObj, yId, objects[i].y);
        env->SetFloatField(jObj, wId, objects[i].w);
        env->SetFloatField(jObj, hId, objects[i].h);
        env->SetObjectField(jObj, labelId, env->NewStringUTF(class_names[objects[i].label]));
        env->SetFloatField(jObj, probId, objects[i].prob);

        env->SetObjectArrayElement(jObjArray, i, jObj);
    }
    return jObjArray; */
}

}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_distbit_nebuia_1plugin_core_Id_GetLabel(JNIEnv *env, jobject thiz, jobject bitmap) {
    auto objects = Id::inference->detect(env, bitmap, 3);
    static const char *class_names[] = {
            "mx_id_back", "mx_id_front", "mx_passport_front"};

    if (!objects.empty()) {
        return env->NewStringUTF(class_names[objects[0].label]);
    }

    return env->NewStringUTF("not_valid");
}