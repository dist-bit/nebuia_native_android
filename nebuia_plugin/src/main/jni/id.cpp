//
// Created by Miguel Angel on 24/07/21.
//

#include <string>
#include <vector>
#include "id.h"

Inference *Id::inference = nullptr;

extern "C" {

// FIXME DeleteGlobalRef is missing for objCls
static jclass objCls = nullptr;
static jmethodID constructorId;
static jfieldID xId;
static jfieldID yId;
static jfieldID wId;
static jfieldID hId;
static jfieldID labelId;
static jfieldID probId;

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    __android_log_print(ANDROID_LOG_DEBUG, "NebuIA", "JNI_OnLoad");
    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    __android_log_print(ANDROID_LOG_DEBUG, "NebuIA", "JNI_OnUnload");
}

JNIEXPORT jboolean JNICALL
Java_com_distbit_nebuia_1plugin_core_Id_Init(JNIEnv *env, jobject, jobject assetManager) {

    if (Id::inference != nullptr) {
        delete Id::inference;
        Id::inference = nullptr;
    }
    if (Id::inference == nullptr) {
        AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
        Id::inference = new Inference(mgr, "det0.param", "det0.bin");
    }

    // init jni glue
    jclass localObjCls = env->FindClass("com/distbit/nebuia_plugin/core/Id$Obj");
    objCls = reinterpret_cast<jclass>(env->NewGlobalRef(localObjCls));

    constructorId = env->GetMethodID(objCls, "<init>", "(Lcom/distbit/nebuia_plugin/core/Id;)V");

    xId = env->GetFieldID(objCls, "x", "F");
    yId = env->GetFieldID(objCls, "y", "F");
    wId = env->GetFieldID(objCls, "w", "F");
    hId = env->GetFieldID(objCls, "h", "F");
    labelId = env->GetFieldID(objCls, "label", "Ljava/lang/String;");
    probId = env->GetFieldID(objCls, "prob", "F");

    return JNI_TRUE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_distbit_nebuia_1plugin_core_Id_Detect(JNIEnv *env, jobject thiz, jobject bitmap) {

    auto objects = Id::inference->detect(env, bitmap, 3);
    static const char *class_names[] = {
            "mx_id_back", "mx_id_front", "mx_passport_front"};

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

}