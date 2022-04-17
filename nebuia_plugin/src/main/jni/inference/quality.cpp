#include "inference.h"
#include "cpu.h"
#include "quality.h"

Quality::Quality(AAssetManager *mgr, const char *param, const char *bin) {
    Net = new ncnn::Net();

    ncnn::Option opt;
    opt.lightmode = true;
    opt.blob_allocator = &blob_pool_allocator;
    opt.workspace_allocator = &workspace_pool_allocator;
    opt.use_packing_layout = true;
    //if (ncnn::get_gpu_count() != 0)
    //    opt.use_vulkan_compute = true;

    Net->opt = opt;

    // init param
    Net->load_param(mgr, param);
    Net->load_model(mgr, bin);
}

Quality::~Quality() {
    // destroy gpu instances
    //ncnn::destroy_gpu_instance();
    Net->clear();
    delete Net;
}

float Quality::quality(JNIEnv *env, jobject bitmap) const {
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    ncnn::Mat in = ncnn::Mat::from_android_bitmap_resize(env, bitmap, ncnn::Mat::PIXEL_RGB, target_size, target_size);

    in.substract_mean_normalize(mean_values, norm_values);
    ncnn::Extractor ex = Net->create_extractor();
    //ex.set_vulkan_compute(true);
    ex.input("mobilenetv2_1.00_224_input_blob", in);

    ncnn::Mat out;
    ex.extract("dense_blob", out);
    float quality = out[0];

    // clear mat image
    in.release();
    out.release();

    return quality;

}
