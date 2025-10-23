//
// Created by aloyenz on 21.10.25.
//

#include <iso646.h>
#include <linux/uinput.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>

#include "ru_aloyenz_t501_driver_virtual_VMouse.h"

JNIEXPORT jlong JNICALL Java_ru_aloyenz_t501_driver_virtual_VMouse_initialize
        (JNIEnv *env, jclass clazz, jstring deviceName) {
    const char *name = (*env)->GetStringUTFChars(env, deviceName, NULL);
    if (name == NULL) {
        return -1; // Error while getting string chars
    }

    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        return -3;
    }

    // Initializing uinput device capabilities
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    ioctl(fd, UI_SET_KEYBIT, BTN_LEFT);
    ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT);
    ioctl(fd, UI_SET_KEYBIT, BTN_MIDDLE);

    ioctl(fd, UI_SET_EVBIT, EV_REL);
    ioctl(fd, UI_SET_RELBIT, REL_X);
    ioctl(fd, UI_SET_RELBIT, REL_Y);
    ioctl(fd, UI_SET_RELBIT, REL_WHEEL);    // Vertical scroll
    ioctl(fd, UI_SET_RELBIT, REL_HWHEEL);   // Horizontal scroll

    // Setting up absolute axes
    struct uinput_abs_setup abs_setup;

    // Setting up uinput device (without absres setup)
    struct uinput_setup setup;
    memset(&setup, 0, sizeof(setup));
    snprintf(setup.name, UINPUT_MAX_NAME_SIZE, "%s", name);
    setup.id.bustype = BUS_USB;
    setup.id.vendor = 0x1984;
    setup.id.product = 0x2025;
    setup.id.version = 1;

    if (ioctl(fd, UI_DEV_SETUP, &setup) < 0) {
        close(fd);
        (*env)->ReleaseStringUTFChars(env, deviceName, name);
        return -1;
    }

    // Creating uinput device
    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        close(fd);
        (*env)->ReleaseStringUTFChars(env, deviceName, name);
        return -2; // Error while creating uinput device
    }

    (*env)->ReleaseStringUTFChars(env, deviceName, name);
    return (jlong)fd; // Return file descriptor as jlong
}

JNIEXPORT jint JNICALL Java_ru_aloyenz_t501_driver_virtual_VMouse_mouseEvent
    (JNIEnv *env, jclass class, jlong fdPointer, jintArray keyCodes, jbooleanArray isRel, jintArray values) {

    struct input_event ev;
    memset(&ev, 0, sizeof(ev));

    jsize keyCount = (*env)->GetArrayLength(env, keyCodes);
    jint *keys = (*env)->GetIntArrayElements(env, keyCodes, NULL);
    jboolean *rels = (*env)->GetBooleanArrayElements(env, isRel, NULL);
    jint *vals = (*env)->GetIntArrayElements(env, values, NULL);

    for (jsize i = 0; i < keyCount; i++) {
        ev.type = rels[i] ? EV_REL : EV_KEY;
        ev.code = keys[i];
        ev.value = vals[i];

        if (write((int)fdPointer, &ev, sizeof(ev)) < 0) {
            // Release resources
            (*env)->ReleaseIntArrayElements(env, keyCodes, keys, 0);
            (*env)->ReleaseBooleanArrayElements(env, isRel, rels, 0);
            (*env)->ReleaseIntArrayElements(env, values, vals, 0);
            return -1; // Error while writing event
        }
    }

    // Sync event
    ev.type = EV_SYN;
    ev.code = SYN_REPORT;
    ev.value = 0;
    if (write((int)fdPointer, &ev, sizeof(ev)) < 0) {
        (*env)->ReleaseIntArrayElements(env, keyCodes, keys, 0);
        (*env)->ReleaseBooleanArrayElements(env, isRel, rels, 0);
        (*env)->ReleaseIntArrayElements(env, values, vals, 0);
        return -2; // Error writing sync
    }

    return 0;
}


JNIEXPORT void JNICALL Java_ru_aloyenz_t501_driver_virtual_VMouse_shutdown(
    JNIEnv *env, jclass class, jlong fdPointer) {
  if (fdPointer > 0) {
    // Destroy uinput device
    ioctl((int)fdPointer, UI_DEV_DESTROY);
    // Close
    close((int)fdPointer);
  }
}