//
// Created by aloyenz on 21.10.25.
//

#include <linux/uinput.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>

#include "ru_aloyenz_t501_driver_VPen.h"

JNIEXPORT jlong JNICALL Java_ru_aloyenz_t501_driver_VPen_initialize
        (JNIEnv *env, jclass clazz, jstring deviceName) {
    const char *name = (*env)->GetStringUTFChars(env, deviceName, NULL);
    if (name == NULL) {
        return -1; // Error while getting string chars
    }

    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        return -3;
    }

    // Initializing uinput
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    ioctl(fd, UI_SET_KEYBIT, BTN_TOUCH);
    ioctl(fd, UI_SET_EVBIT, EV_ABS);
    ioctl(fd, UI_SET_ABSBIT, ABS_X);
    ioctl(fd, UI_SET_ABSBIT, ABS_Y);
    ioctl(fd, UI_SET_ABSBIT, ABS_PRESSURE);
    ioctl(fd, UI_SET_ABSBIT, ABS_TILT_X);
    ioctl(fd, UI_SET_ABSBIT, ABS_TILT_Y);

    // Setting up uinput device
    struct uinput_user_dev uidev;
    memset(&uidev, 0, sizeof(uidev));
    snprintf(uidev.name, UINPUT_MAX_NAME_SIZE, "%s", name);
    uidev.id.bustype = BUS_USB;
    uidev.id.vendor = 0x1984;
    uidev.id.product = 0x2022;
    uidev.id.version = 1;
    uidev.absmin[ABS_X] = 0;
    uidev.absmax[ABS_X] = 4096;
    uidev.absmin[ABS_Y] = 0;
    uidev.absmax[ABS_Y] = 4096;
    uidev.absmin[ABS_PRESSURE] = 0;
    uidev.absmax[ABS_PRESSURE] = 1024;
    uidev.absmin[ABS_TILT_X] = -90;
    uidev.absmax[ABS_TILT_X] = 90;
    uidev.absmin[ABS_TILT_Y] = -90;
    uidev.absmax[ABS_TILT_Y] = 90;

    if (write(fd, &uidev, sizeof(uidev)) < 0) {
        close(fd);
        (*env) -> ReleaseStringUTFChars(env, deviceName, name);
        return -1;
    }

    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        close(fd);
        (*env)->ReleaseStringUTFChars(env, deviceName, name);
        return -2; // Error while creating uinput device
    }

    (*env)->ReleaseStringUTFChars(env, deviceName, name);
    return (jlong)fd; // Return file descriptor as jlong
}

JNIEXPORT jint JNICALL Java_ru_aloyenz_t501_driver_VPen_writePosition
        (JNIEnv *env, jclass clazz, jlong fd, jint x, jint y, jint pressure, jboolean touch, jint tiltX, jint tiltY) {
    if (fd <= 0) {
        return -1; // Invalid file descriptor
    }


    struct input_event ev;
    memset(&ev, 0, sizeof(ev));

    // Write X coordinate
    ev.type = EV_ABS;
    ev.code = ABS_X;
    ev.value = x;
    if (write(fd, &ev, sizeof(ev)) < 0) {
        return -2; // Error writing X
    }

    // Write Y coordinate
    ev.code = ABS_Y;
    ev.value = y;
    if (write(fd, &ev, sizeof(ev)) < 0) {
        return -3; // Error writing Y
    }

    // Write pressure
    ev.code = ABS_PRESSURE;
    ev.value = pressure;
    if (write(fd, &ev, sizeof(ev)) < 0) {
        return -4; // Error writing pressure
    }

    // Write tilt X
    ev.code = ABS_TILT_X;
    ev.value = tiltX;
    if (write(fd, &ev, sizeof(ev)) < 0) {
        return -5; // Error writing tilt X
    }

    // Write tilt Y
    ev.code = ABS_TILT_Y;
    ev.value = tiltY;
    if (write(fd, &ev, sizeof(ev)) < 0) {
        return -6; // Error writing tilt Y
    }

    // Write touch event
    ev.type = EV_KEY;
    ev.code = BTN_TOUCH;
    ev.value = touch ? 1 : 0;
    if (write(fd, &ev, sizeof(ev)) < 0) {
        return -7; // Error writing touch
    }

    // Synchronize events
    ev.type = EV_SYN;
    ev.code = SYN_REPORT;
    ev.value = 0;
    if (write(fd, &ev, sizeof(ev)) < 0) {
        return -8; // Error writing sync
    }

    return 0; // Success
}

JNIEXPORT void JNICALL Java_ru_aloyenz_t501_driver_VPen_shutdown
(JNIEnv *env, jclass class, jlong fdPointer) {
    if (fdPointer > 0) {
        // Destroy uinput device
        ioctl((int)fdPointer, UI_DEV_DESTROY);
        // Close
        close((int)fdPointer);
    }
}
