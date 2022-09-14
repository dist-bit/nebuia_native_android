//
// Created by Miguel Angel on 24/07/21.
//

#ifndef Finger_H
#define Finger_H

#include <android/asset_manager_jni.h>
#include "net.h"
#include "id.h"
#include "quality.h"
#include <jni.h>

class Finger {
public:
    static Inference *inference;
    static Quality *quality;
};

#endif //Finger_H