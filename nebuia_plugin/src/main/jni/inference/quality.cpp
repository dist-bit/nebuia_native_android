#include "inference.h"
#include "cpu.h"
#include "quality.h"

Quality::Quality(AAssetManager *mgr, const char *param, const char *bin) {
    Net = new ncnn::Net();

    ncnn::Option opt;
    opt.blob_allocator = &blob_pool_allocator;
    opt.workspace_allocator = &workspace_pool_allocator;
    Net->opt = opt;

    // init param
    Net->load_param(mgr, param);
    Net->load_model(mgr, bin);
}

Quality::~Quality() {
    Net->clear();
    delete Net;
}

float Quality::quality(JNIEnv *env, jobject bitmap) const {
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    ncnn::Mat in = ncnn::Mat::from_android_bitmap_resize(env, bitmap, ncnn::Mat::PIXEL_BGR2RGB, target_size, target_size);

    in.substract_mean_normalize(mean_values, norm_values);
    ncnn::Extractor ex = Net->create_extractor();
    //ex.set_num_threads(ncnn::get_big_cpu_count());

    ex.input("input", in);

    ncnn::Mat out;
    ex.extract("output", out);
    // Get the binary prediction
    const float* prob = out.channel(1);
    float score = prob[0];


    // clear mat image
    in.release();
    out.release();
    return score;
}


