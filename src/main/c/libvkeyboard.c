//
// Created by aloyenz on 21.10.25.
//

#include <iso646.h>
#include <linux/uinput.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>

#include "ru_aloyenz_t501_driver_virtual_VKeyboard.h"

JNIEXPORT jlong JNICALL Java_ru_aloyenz_t501_driver_virtual_VKeyboard_initialize
        (JNIEnv *env, jclass clazz, jstring deviceName, jintArray keyCodesToRegister) {
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
    ioctl(fd, UI_SET_EVBIT, EV_REP);

    // Keycodes
    jsize keyCount = (*env)->GetArrayLength(env, keyCodesToRegister);
    jint *keys = (*env)->GetIntArrayElements(env, keyCodesToRegister, NULL);
    for (jsize i = 0; i < keyCount; i++) {
        ioctl(fd, UI_SET_KEYBIT, keys[i]);
    }

    // Additional keycodes
    for (int i = 0; i < 248; i++) {
        ioctl(fd, UI_SET_KEYBIT, i);
    }

    // Releasing
    (*env)->ReleaseIntArrayElements(env, keyCodesToRegister, keys, 0);

    // Setting up absolute axes
    struct uinput_abs_setup abs_setup;

    // Setting up uinput device (without absres setup)
    struct uinput_setup setup;
    memset(&setup, 0, sizeof(setup));
    snprintf(setup.name, UINPUT_MAX_NAME_SIZE, "%s", name);
    setup.id.bustype = BUS_USB;
    setup.id.vendor = 0x1984;
    setup.id.product = 0x2023;
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

JNIEXPORT jint JNICALL Java_ru_aloyenz_t501_driver_virtual_VKeyboard_keyboardKeyEvent
  (JNIEnv *env, jclass class, jlong fdPointer, jintArray keyCodes, jbooleanArray keyStates) {

    struct input_event ev;
    memset(&ev, 0, sizeof(ev));

    jsize keyCount = (*env)->GetArrayLength(env, keyCodes);
    jint *keys = (*env)->GetIntArrayElements(env, keyCodes, NULL);

    jsize statesCount = (*env)->GetArrayLength(env, keyStates);
    jboolean *states = (*env)->GetBooleanArrayElements(env, keyStates, NULL);

    for (jsize i = 0; i < keyCount && i < statesCount; i++) {
        ev.type = EV_KEY;
        ev.code = keys[i];
        ev.value = states[i] ? 1 : 0;
        if (write((int)fdPointer, &ev, sizeof(ev)) < 0) {
            (*env)->ReleaseIntArrayElements(env, keyCodes, keys, 0);
            (*env)->ReleaseBooleanArrayElements(env, keyStates, states, 0);

            return -1; // Error writing key event
        }
    }

    // Synchronize events
    ev.type = EV_SYN;
    ev.code = SYN_REPORT;
    ev.value = 0;
    if (write((int)fdPointer, &ev, sizeof(ev)) < 0) {
        (*env)->ReleaseIntArrayElements(env, keyCodes, keys, 0);
        (*env)->ReleaseBooleanArrayElements(env, keyStates, states, 0);
        return -2; // Error writing sync
    }

    // Releasing...
    (*env)->ReleaseIntArrayElements(env, keyCodes, keys, 0);
    (*env)->ReleaseBooleanArrayElements(env, keyStates, states, 0);

    return 0;
}

JNIEXPORT void JNICALL Java_ru_aloyenz_t501_driver_virtual_VKeyboard_shutdown(
    JNIEnv *env, jclass class, jlong fdPointer) {
  if (fdPointer > 0) {
    // Destroy uinput device
    ioctl((int)fdPointer, UI_DEV_DESTROY);
    // Close
    close((int)fdPointer);
  }
}