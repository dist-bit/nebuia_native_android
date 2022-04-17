//
// Created by Miguel Angel on 24/07/21.
//

#ifndef Id_H
#define Id_H

#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <jni.h>
#include "net.h"
#include "inference/inference.h"

class Id {
public:
    static Inference *inference;
};

#endif //Id