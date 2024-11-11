//
// Created by Miguel Angel on 10/08/21.
//

#define clip(x, y) (x < 0 ? 0 : (x > y ? y : x))

#include <cpu.h>
#include "face.h"
#include "mat.h"

Face* Face::detector = nullptr;

Face::Face(AAssetManager *mgr, const char *param, const char *bin, float iou_threshold_, int topk_) {
    topk = topk_;
    score_threshold = 0.7;
    iou_threshold = iou_threshold_;
    in_w = 320;
    in_h = 320;
    w_h_list = {in_w, in_h};

    for (auto size : w_h_list) {
        std::vector<float> fm_item;
        for (float stride : strides) {
            fm_item.push_back(ceil(size / stride));
        }
        featuremap_size.push_back(fm_item);
    }

    for (auto size : w_h_list) {
        shrinkage_size.push_back(strides);
    }

    /* generate prior anchors */
    for (int index = 0; index < num_featuremap; index++) {
        float scale_w = in_w / shrinkage_size[0][index];
        float scale_h = in_h / shrinkage_size[1][index];
        for (int j = 0; j < featuremap_size[1][index]; j++) {
            for (int i = 0; i < featuremap_size[0][index]; i++) {
                float x_center = (i + 0.5) / scale_w;
                float y_center = (j + 0.5) / scale_h;

                for (float k : min_boxes[index]) {
                    float w = k / in_w;
                    float h = k / in_h;
                    priors.push_back({clip(x_center, 1), clip(y_center, 1), clip(w, 1), clip(h, 1)});
                }
            }
        }
    }
    num_anchors = priors.size();

    this->Net = new ncnn::Net();

    ncnn::Option opt;
    ncnn::set_omp_num_threads(ncnn::get_big_cpu_count());
    opt.num_threads = ncnn::get_big_cpu_count();
    opt.use_packing_layout = true;

    this->Net->opt = opt;

    this->Net->load_param(mgr, param);
    this->Net->load_model(mgr, bin);
}

Face::~Face() {
    // destroy gpu instances
    //ncnn::destroy_gpu_instance();
    this->Net->clear();
}

std::vector<BoxInfo> Face::detect(JNIEnv *env, jobject image) {

    AndroidBitmapInfo img_size;
    AndroidBitmap_getInfo(env, image, &img_size);
    ncnn::Mat img = ncnn::Mat::from_android_bitmap(env, image, ncnn::Mat::PIXEL_BGR2RGB);

    std::vector<BoxInfo> dets;
    image_h = img.h;
    image_w = img.w;

    ncnn::Mat in;
    ncnn::resize_bilinear(img, in, in_w, in_h);
    ncnn::Mat ncnn_img = in;
    ncnn_img.substract_mean_normalize(mean_vals, norm_vals);

    std::vector<BoxInfo> bbox_collection;
    std::vector<BoxInfo> valid_input;

    ncnn::Extractor ex = this->Net->create_extractor();
    //ex.set_vulkan_compute(true);
    ex.set_num_threads(ncnn::get_big_cpu_count());
    ex.input("input", ncnn_img);

    ncnn::Mat scores;
    ncnn::Mat boxes;
    ex.extract("scores", scores);
    ex.extract("boxes", boxes);
    generateBBox(bbox_collection, scores, boxes, score_threshold, num_anchors);
    nms(bbox_collection, dets);
    return dets;
}

void Face::nms(std::vector<BoxInfo> &input, std::vector<BoxInfo> &output, int type) const {
    std::sort(input.begin(), input.end(), [](const BoxInfo &a, const BoxInfo &b) { return a.prob > b.prob; });

    int box_num = input.size();

    std::vector<int> merged(box_num, 0);

    for (int i = 0; i < box_num; i++) {
        if (merged[i])
            continue;
        std::vector<BoxInfo> buf;

        buf.push_back(input[i]);
        merged[i] = 1;

        float h0 = input[i].y2 - input[i].y1 + 1;
        float w0 = input[i].x2 - input[i].x1 + 1;

        float area0 = h0 * w0;

        for (int j = i + 1; j < box_num; j++) {
            if (merged[j])
                continue;

            float inner_x0 = input[i].x1 > input[j].x1 ? input[i].x1 : input[j].x1;
            float inner_y0 = input[i].y1 > input[j].y1 ? input[i].y1 : input[j].y1;

            float inner_x1 = input[i].x2 < input[j].x2 ? input[i].x2 : input[j].x2;
            float inner_y1 = input[i].y2 < input[j].y2 ? input[i].y2 : input[j].y2;

            float inner_h = inner_y1 - inner_y0 + 1;
            float inner_w = inner_x1 - inner_x0 + 1;

            if (inner_h <= 0 || inner_w <= 0)
                continue;

            float inner_area = inner_h * inner_w;

            float h1 = input[j].y2 - input[j].y1 + 1;
            float w1 = input[j].x2 - input[j].x1 + 1;

            float area1 = h1 * w1;

            float score;

            score = inner_area / (area0 + area1 - inner_area);

            if (score > iou_threshold) {
                merged[j] = 1;
                buf.push_back(input[j]);
            }
        }
        switch (type) {
            case hard_nms: {
                output.push_back(buf[0]);
                break;
            }
            case blending_nms: {
                float total = 0;
                for (int i = 0; i < buf.size(); i++) {
                    total += exp(buf[i].prob);
                }
                BoxInfo rects;
                memset(&rects, 0, sizeof(rects));
                for (int i = 0; i < buf.size(); i++) {
                    float rate = exp(buf[i].prob) / total;
                    rects.x1 += buf[i].x1 * rate;
                    rects.y1 += buf[i].y1 * rate;
                    rects.x2 += buf[i].x2 * rate;
                    rects.y2 += buf[i].y2 * rate;
                    rects.prob += buf[i].prob * rate;
                }
                output.push_back(rects);
                break;
            }
            default: {
                printf("wrong type of nms.");
                exit(-1);
            }
        }
    }
}

void Face::generateBBox(std::vector<BoxInfo> &bbox_collection, ncnn::Mat scores, ncnn::Mat boxes, float score_threshold, int num_anchors) {
    for (int i = 0; i < num_anchors; i++) {
        if (scores.channel(0)[i * 2 + 1] > score_threshold) {
            BoxInfo rects;
            float x_center = boxes.channel(0)[i * 4] * center_variance * priors[i][2] + priors[i][0];
            float y_center = boxes.channel(0)[i * 4 + 1] * center_variance * priors[i][3] + priors[i][1];
            float w = exp(boxes.channel(0)[i * 4 + 2] * size_variance) * priors[i][2];
            float h = exp(boxes.channel(0)[i * 4 + 3] * size_variance) * priors[i][3];

            rects.x1 = clip(x_center - w / 2.0, 1) * image_w;
            rects.y1 = clip(y_center - h / 2.0, 1) * image_h;
            rects.x2 = clip(x_center + w / 2.0, 1) * image_w;
            rects.y2 = clip(y_center + h / 2.0, 1) * image_h;
            rects.prob = clip(scores.channel(0)[i * 2 + 1], 1);
            bbox_collection.push_back(rects);
        }
    }
}


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

JNIEXPORT jboolean JNICALL
Java_com_distbit_nebuia_1plugin_core_Face_Init(JNIEnv *env, jobject thiz, jobject assetManager) {
    if (Face::detector != nullptr) {
        delete Face::detector;
        Face::detector = nullptr;
    }
    if (Face::detector == nullptr) {
        AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
        Face::detector = new Face(mgr, "det1.param", "det1.bin");
    }

    // init jni glue
    jclass localObjCls = env->FindClass("com/distbit/nebuia_plugin/core/Face$Obj");
    objCls = reinterpret_cast<jclass>(env->NewGlobalRef(localObjCls));

    constructorId = env->GetMethodID(objCls, "<init>", "(Lcom/distbit/nebuia_plugin/core/Face;)V");

    xId = env->GetFieldID(objCls, "x", "F");
    yId = env->GetFieldID(objCls, "y", "F");
    wId = env->GetFieldID(objCls, "w", "F");
    hId = env->GetFieldID(objCls, "h", "F");
    labelId = env->GetFieldID(objCls, "label", "Ljava/lang/String;");
    probId = env->GetFieldID(objCls, "prob", "F");

    return JNI_TRUE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_distbit_nebuia_1plugin_core_Face_detect(JNIEnv *env, jclass thiz, jobject image) {

    auto objects = Face::detector->detect(env, image);
    jobjectArray jObjArray = env->NewObjectArray(objects.size(), objCls, nullptr);

    for (size_t i = 0; i < objects.size(); i++) {
        jobject jObj = env->NewObject(objCls, constructorId, thiz);

        env->SetFloatField(jObj, xId, objects[i].x1);
        env->SetFloatField(jObj, yId, objects[i].y1);
        env->SetFloatField(jObj, wId, objects[i].x2);
        env->SetFloatField(jObj, hId, objects[i].y2);
        env->SetObjectField(jObj, labelId, env->NewStringUTF("face"));
        env->SetFloatField(jObj, probId, objects[i].prob);

        env->SetObjectArrayElement(jObjArray, i, jObj);
    }
    return jObjArray;
}
}
