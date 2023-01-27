//
// Created by miguel on 09/11/22.
//

#include "document_extractor.h"
#include "cpu.h"
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#define clip(x, y) (x < 0 ? 0 : (x > y ? y : x))

static inline float intersection_area(const Doc& a, const Doc& b)
{
    cv::Rect_<float> inter = a.rect & b.rect;
    return inter.area();
}

static void qsort_descent_inplace(std::vector<Doc>& faceobjects, int left, int right)
{
    int i = left;
    int j = right;
    float p = faceobjects[(left + right) / 2].prob;

    while (i <= j)
    {
        while (faceobjects[i].prob > p)
            i++;

        while (faceobjects[j].prob < p)
            j--;

        if (i <= j)
        {
            // swap
            std::swap(faceobjects[i], faceobjects[j]);

            i++;
            j--;
        }
    }

#pragma omp parallel sections
    {
#pragma omp section
        {
            if (left < j) qsort_descent_inplace(faceobjects, left, j);
        }
#pragma omp section
        {
            if (i < right) qsort_descent_inplace(faceobjects, i, right);
        }
    }
}

static void qsort_descent_inplace(std::vector<Doc>& faceobjects)
{
    if (faceobjects.empty())
        return;

    qsort_descent_inplace(faceobjects, 0, faceobjects.size() - 1);
}

static void nms_sorted_bboxes(std::vector<Doc>& faceobjects, std::vector<int>& picked, float nms_threshold)
{
    picked.clear();

    const int n = faceobjects.size();

    std::vector<float> areas(n);
    for (int i = 0; i < n; i++)
    {
        areas[i] = faceobjects[i].rect.area();
    }

    for (int i = 0; i < n; i++)
    {
        const Doc& a = faceobjects[i];

        int keep = 1;
        for (int j : picked)
        {
            const Doc& b = faceobjects[j];

            // intersection over union
            float inter_area = intersection_area(a, b);
            float union_area = areas[i] + areas[j] - inter_area;
            // float IoU = inter_area / union_area
            if (inter_area / union_area > nms_threshold)
                keep = 0;
        }

        if (keep)
            picked.push_back(i);
    }
}

static inline float sigmoid(float x)
{
    return 1.0f / (1.0f + exp(-x));
}

static void generate_proposals(const ncnn::Mat& anchors, int stride, const ncnn::Mat& in_pad, const ncnn::Mat& feat_blob, float prob_threshold, std::vector<Doc>& objects)
{
    const int num_grid = feat_blob.h;

    int num_grid_x;
    int num_grid_y;
    if (in_pad.w > in_pad.h)
    {
        num_grid_x = in_pad.w / stride;
        num_grid_y = num_grid / num_grid_x;
    }
    else
    {
        num_grid_y = in_pad.h / stride;
        num_grid_x = num_grid / num_grid_y;
    }

    const int num_class = 1;

    const int num_anchors = anchors.w / 2;

    for (int q = 0; q < num_anchors; q++)
    {
        const float anchor_w = anchors[q * 2];
        const float anchor_h = anchors[q * 2 + 1];

        const ncnn::Mat feat = feat_blob.channel(q);

        for (int i = 0; i < num_grid_y; i++)
        {
            for (int j = 0; j < num_grid_x; j++)
            {
                const float* featptr = feat.row(i * num_grid_x + j);
                float box_confidence = sigmoid(featptr[4]);
                if (box_confidence >= prob_threshold)
                {
                    // find class index with max class score
                    int class_index = 0;
                    float class_score = -FLT_MAX;
                    for (int k = 0; k < num_class; k++)
                    {
                        float score = featptr[5 + k];// +15];
                        if (score > class_score)
                        {
                            class_index = k;
                            class_score = score;
                        }
                    }
                    float confidence = box_confidence * sigmoid(class_score);
                    if (confidence >= prob_threshold)
                    {
                        float dx = sigmoid(featptr[0]);
                        float dy = sigmoid(featptr[1]);
                        float dw = sigmoid(featptr[2]);
                        float dh = sigmoid(featptr[3]);

                        float pb_cx = (dx * 2.f - 0.5f + j) * stride;
                        float pb_cy = (dy * 2.f - 0.5f + i) * stride;

                        float pb_w = pow(dw * 2.f, 2) * anchor_w;
                        float pb_h = pow(dh * 2.f, 2) * anchor_h;

                        float x0 = pb_cx - pb_w * 0.5f;
                        float y0 = pb_cy - pb_h * 0.5f;
                        float x1 = pb_cx + pb_w * 0.5f;
                        float y1 = pb_cy + pb_h * 0.5f;

                        Doc obj;
                        obj.rect.x = x0;
                        obj.rect.y = y0;
                        obj.rect.width = x1 - x0;
                        obj.rect.height = y1 - y0;
                        obj.label = class_index;
                        obj.prob = confidence;
                        for (int l = 0; l < 5; l++)
                        {
                            Landmarks pt;
                            pt.x = (featptr[3 * l + 6] * 2-0.5 +  j) * stride;
                            pt.y = (featptr[3 * l + 1 + 6] * 2-0.5 + i) * stride;
                           //pt.score = sigmoid(featptr[3 * l + 2 + 6]);
                            obj.pts.push_back(pt);
                        }
                        objects.push_back(obj);
                    }
                }
            }
        }
    }
}

DocumentExtractor::~DocumentExtractor() {
    Net->clear();
    delete Net;
}

DocumentExtractor::DocumentExtractor(AAssetManager *mgr, const char *param, const char *bin) {
    Net = new ncnn::Net();

    blob_pool_allocator.set_size_compare_ratio(0.f);
    workspace_pool_allocator.set_size_compare_ratio(0.f);

    ncnn::Option opt;
    ncnn::set_omp_num_threads(ncnn::get_big_cpu_count());
    opt.num_threads = ncnn::get_big_cpu_count();
    opt.use_packing_layout = true;
    opt.blob_allocator = &blob_pool_allocator;
    opt.workspace_allocator = &workspace_pool_allocator;
    Net->opt = opt;
    // init param
    Net->load_param(mgr, param);
    Net->load_model(mgr, bin);

}

std::vector<Doc>
DocumentExtractor::detect(JNIEnv *env, jobject bitmap, std::vector<Doc>& objects) {

    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    const int img_w = info.width;
    const int img_h = info.height;

    int w = img_w;
    int h = img_h;

    float scale = 1.f;
    if (w > h)
    {
        scale = (float)target_size / w;
        w = target_size;
        h = h * scale;
    }
    else
    {
        scale = (float)target_size / h;
        h = target_size;
        w = w * scale;
    }

    ncnn::Mat in = ncnn::Mat::from_android_bitmap_resize(env, bitmap, ncnn::Mat::PIXEL_BGR2RGB, w, h);
    int wpad = target_size - w; //(w + 31) / 32 * 32 - w;
    int hpad = target_size - h; //(h + 31) / 32 * 32 - h;
    ncnn::Mat in_pad;
    ncnn::copy_make_border(in, in_pad, hpad / 2, hpad - hpad / 2, wpad / 2, wpad - wpad / 2, ncnn::BORDER_CONSTANT, 114.f);

    in_pad.substract_mean_normalize(0, norm_vals);

    ncnn::Extractor ex = Net->create_extractor();
    ex.set_num_threads(ncnn::get_big_cpu_count());

    ex.input("data", in_pad);

    std::vector<Doc> proposals;

    // stride 8
    {
        ncnn::Mat out;
        ex.extract("stride_8", out);

        ncnn::Mat anchors(6);
        anchors[0] = 4.f;
        anchors[1] = 5.f;
        anchors[2] = 6.f;
        anchors[3] = 8.f;
        anchors[4] = 10.f;
        anchors[5] = 12.f;

        std::vector<Doc> objects8;
        generate_proposals(anchors, 8, in_pad, out, prob_threshold, objects8);

        proposals.insert(proposals.end(), objects8.begin(), objects8.end());
    }

    // stride 16
    {
        ncnn::Mat out;

        ex.extract("stride_16", out);

        ncnn::Mat anchors(6);
        anchors[0] = 15.f;
        anchors[1] = 19.f;
        anchors[2] = 23.f;
        anchors[3] = 30.f;
        anchors[4] = 39.f;
        anchors[5] = 52.f;

        std::vector<Doc> objects16;
        generate_proposals(anchors, 16, in_pad, out, prob_threshold, objects16);

        proposals.insert(proposals.end(), objects16.begin(), objects16.end());
    }

    // stride 32
    {
        ncnn::Mat out;

        ex.extract("stride_32", out);

        ncnn::Mat anchors(6);
        anchors[0] = 72.f;
        anchors[1] = 97.f;
        anchors[2] = 123.f;
        anchors[3] = 164.f;
        anchors[4] = 209.f;
        anchors[5] = 297.f;

        std::vector<Doc> objects32;
        generate_proposals(anchors, 32, in_pad, out, prob_threshold, objects32);

        proposals.insert(proposals.end(), objects32.begin(), objects32.end());
    }

    // sort all proposals by score from highest to lowest
    qsort_descent_inplace(proposals);

    // apply nms with nms_threshold
    std::vector<int> picked;
    nms_sorted_bboxes(proposals, picked, nms_threshold);

    int count = picked.size();

   // objects.resize(count);

    for (int i = 0; i < count; i++)
    {
        //__android_log_print(ANDROID_LOG_ERROR, "TRACKERS", "%d", picked[i]);
        //__android_log_print(ANDROID_LOG_ERROR, "TRACKERS_1", "%d", proposals[picked[i]].label);
        Doc a =  proposals[picked[i]];
        // adjust offset to original unpadded
        float x0 = (a.rect.x - (wpad / 2)) / scale;
        float y0 = (a.rect.y - (hpad / 2)) / scale;
        float x1 = (a.rect.x + a.rect.width - (wpad / 2)) / scale;
        float y1 = (a.rect.y + a.rect.height - (hpad / 2)) / scale;

        a.pts.erase(a.pts.begin() + 2);

        for (auto & pt : a.pts)
        {
            float x = (pt.x - (wpad / 2)) / scale;
            float y = (pt.y - (hpad / 2)) / scale;
            pt.x = x;
            pt.y = y;
        }

        // clip
        x0 = std::max(std::min(x0, (float)(img_w - 1)), 0.f);
        y0 = std::max(std::min(y0, (float)(img_h - 1)), 0.f);
        x1 = std::max(std::min(x1, (float)(img_w - 1)), 0.f);
        y1 = std::max(std::min(y1, (float)(img_h - 1)), 0.f);

        a.rect.x = x0;
        a.rect.y = y0;
        a.rect.width = x1 - x0;
        a.rect.height = y1 - y0;

        objects.push_back(a);
    }




    return objects;
}


