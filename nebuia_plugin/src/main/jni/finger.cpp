//
// Created by Miguel Angel on 24/07/21.
//

#include <string>
#include <vector>
#include "finger.h"
#include <android/log.h>
#include "cpu.h"

Inference *Finger::inference = nullptr;
Quality *Finger::quality = nullptr;

extern "C" {

static jclass objCls = nullptr;
static jmethodID constructorId;
static jfieldID xId;
static jfieldID yId;
static jfieldID wId;
static jfieldID hId;
static jfieldID labelId;
static jfieldID probId;

// public native boolean Init(AssetManager mgr);
JNIEXPORT jboolean JNICALL
Java_com_distbit_nebuia_1plugin_core_Finger_Init(JNIEnv *env, jobject, jobject assetManager) {

    if (Finger::inference != nullptr) {
        delete Finger::inference;
        Finger::inference = nullptr;
    }

    if (Finger::quality != nullptr) {
        delete Finger::quality;
        Finger::quality = nullptr;
    }

    if (Finger::inference == nullptr) {
        AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
        Finger::inference = new Inference(mgr, "det3.param", "det3.bin");
    }

    if (Finger::quality == nullptr) {
        AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
        // finger quality net
        Finger::quality = new Quality(mgr, "det4.param", "det4.bin");
    }

    // init jni glue
    jclass localObjCls = env->FindClass("com/distbit/nebuia_plugin/core/Finger$Obj");
    objCls = reinterpret_cast<jclass>(env->NewGlobalRef(localObjCls));

    constructorId = env->GetMethodID(objCls, "<init>",
                                     "(Lcom/distbit/nebuia_plugin/core/Finger;)V");

    xId = env->GetFieldID(objCls, "x", "F");
    yId = env->GetFieldID(objCls, "y", "F");
    wId = env->GetFieldID(objCls, "w", "F");
    hId = env->GetFieldID(objCls, "h", "F");
    labelId = env->GetFieldID(objCls, "label", "Ljava/lang/String;");
    probId = env->GetFieldID(objCls, "prob", "F");

    return JNI_TRUE;
}

// public native Obj[] Detect(Bitmap bitmap, boolean use_gpu);
JNIEXPORT jobjectArray JNICALL
Java_com_distbit_nebuia_1plugin_core_Finger_Detect(JNIEnv *env, jobject thiz, jobject bitmap) {

    auto objects = Finger::inference->detect(env, bitmap, 1);
    static const char *class_names[] = {
            "finger"};

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

    return jObjArray;
}

// public native float Quality(Bitmap bitmap, boolean use_gpu);
JNIEXPORT jfloat JNICALL
Java_com_distbit_nebuia_1plugin_core_Finger_Quality(JNIEnv *env, jobject thiz, jobject bitmap) {
    float score = Finger::quality->quality(env, bitmap);
    return score;
}

}


