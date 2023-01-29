//
// Created by miguel on 12/09/22.
//


#include "utils.h"

class IntensityTransformation {
public:
    static void gamma_transformation(cv::Mat scr, cv::Mat &dst, double gamma) {
        double c;
        double r_min, r_max;
        minMaxLoc(scr, &r_min, &r_max);
        c = 255 / pow(r_max, gamma);
        scr.convertTo(scr, CV_32F);
        pow(scr, gamma, dst);
        dst = c * dst;
        dst.convertTo(dst, CV_8U);
    }
};

void normalize(cv::Mat &im_arr, float M0, float VAR0) {

    int row = im_arr.rows;
    im_arr.convertTo(im_arr, CV_32FC1);
    double M = (1 / pow(row, 2)) * cv::sum(im_arr)[0];

    cv::Mat dst2;
    cv::pow(im_arr - M, 2, dst2);
    double VAR = (1 / pow(row, 2)) * cv::sum(dst2)[0];

    for (int i(0); i < im_arr.rows; i++) {
        for (int j(0); j < im_arr.cols; j++) {
            if (im_arr.at<float>(i, j) > M)
                im_arr.at<float>(i, j) = M0 + sqrt(VAR0 * pow(im_arr.at<float>(i, j) - M, 2) / VAR);
            else
                im_arr.at<float>(i, j) = M0 - sqrt(VAR0 * pow(im_arr.at<float>(i, j) - M, 2) / VAR);
        }
    }
}

cv::Mat Utils::transformMat(JNIEnv *env, jobject process) {
    ncnn::Mat index = ncnn::Mat::from_android_bitmap(env, process, ncnn::Mat::PIXEL_RGB);
    cv::Mat rgb = cv::Mat::zeros(index.h, index.w,CV_8UC3);
    index.to_pixels(rgb.data, ncnn::Mat::PIXEL_RGB);
    index.release();
    return rgb;
}

void Utils::processCLAHE(JNIEnv *env, jobject inBitmap, jobject outBitmap) {

    cv::Mat image = transformMat(env, inBitmap);

    cv::Mat gray;
    cv::cvtColor(image, gray, cv::COLOR_RGB2GRAY);

    cv::Mat blurred;
    cv::GaussianBlur(gray, blurred, cv::Size(11, 11), 10.0);

    cv::Mat unSharped;
    cv::addWeighted(gray, 1.0 + 3.0, blurred, -3.0, 0.0, unSharped);

    cv::Mat im2;
    IntensityTransformation::gamma_transformation(unSharped, im2, 1.63);

    im2 = (255 - im2);
    cv::Ptr<cv::CLAHE> clahe = cv::createCLAHE();
    clahe->setClipLimit(4.0);
    clahe->setTilesGridSize(cv::Size(2, 2));

    cv::Mat dst;
    clahe->apply(im2, dst);

    cv::Mat eqHist;
    cv::equalizeHist(dst, eqHist);

    normalize(eqHist, 0.7, 0.4);

    cv::Mat ucharMat, ucharMatScaled;
    eqHist.convertTo(ucharMat, CV_8UC1);

    // scale values from 0..1 to 0..255
    eqHist.convertTo(ucharMatScaled, CV_8UC1, 255, 0);

    cv::Mat resized;
    cv::resize(ucharMatScaled, resized, cv::Size(416, 416));

    int w = resized.cols;
    int h = resized.rows;

    cv::Point center = cv::Point(208, (int) h / 2);
    cv::Point axes = cv::Point((int) w / 2.9, (int) h / 2.2);
    cv::Scalar color = cv::Scalar(225, 225, 255);

    cv::Mat mask(h, w, CV_8UC1, cv::Scalar(0, 0, 0));
    cv::ellipse(mask, center, axes, 0, 0, 360, color, -1);

    cv::Mat bitwise_and;
    cv::bitwise_and(resized, mask, bitwise_and);

    cv::Mat negative(h, w, CV_8UC1, cv::Scalar(255, 0, 0));
    cv::bitwise_not(negative, negative, mask);

    cv::Mat final;
    cv::add(negative, bitwise_and, final);

    cv::Mat se = cv::getStructuringElement(cv::MORPH_ELLIPSE, cv::Point(6, 6));
    cv::Mat bg;
    cv::morphologyEx(final, bg, cv::MORPH_DILATE, se);
    cv::Mat out;
    cv::divide(final, bg, out, 255);

    cv::Mat flip;
    cv::flip(out, flip, 1);

    ncnn::Mat in2 = ncnn::Mat::from_pixels(flip.data, ncnn::Mat::PIXEL_GRAY, flip.cols, flip.rows);
    in2.to_android_bitmap(env, outBitmap, ncnn::Mat::PIXEL_GRAY);
    in2.release();
    image.release();
}

void Utils::rotate(const cv::Mat in, float angle) {
    cv::Point2f center((in.cols - 1) / 2.0, (in.rows - 1) / 2.0);
    cv::Mat rotation_matix = getRotationMatrix2D(center, angle, 1.0);
    warpAffine(in, in, rotation_matix, in.size());
}

cv::Mat Utils::resize(const cv::Mat &in, int w, int h) {
    cv::Mat out;
    cv::resize(in, out, cv::Size(w, h));
    return out;
}