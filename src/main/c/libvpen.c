//
// Created by aloyenz on 21.10.25.
//

#include <iso646.h>
#include <linux/uinput.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>

#include "ru_aloyenz_t501_driver_virtual_VPen.h"

JNIEXPORT jlong JNICALL Java_ru_aloyenz_t501_driver_virtual_VPen_initialize
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
    ioctl(fd, UI_SET_EVBIT, EV_REP);
    ioctl(fd, UI_SET_KEYBIT, BTN_TOUCH);
    ioctl(fd, UI_SET_KEYBIT, BTN_TOOL_PEN);    // Adding pen tool support
    ioctl(fd, UI_SET_KEYBIT, BTN_STYLUS);      // Adding first button stylus support
    ioctl(fd, UI_SET_KEYBIT, BTN_STYLUS2);     // Adding second button stylus support

    ioctl(fd, UI_SET_EVBIT, EV_ABS);
    ioctl(fd, UI_SET_ABSBIT, ABS_X);
    ioctl(fd, UI_SET_ABSBIT, ABS_Y);
    ioctl(fd, UI_SET_ABSBIT, ABS_PRESSURE);
    ioctl(fd, UI_SET_ABSBIT, ABS_TILT_X);
    ioctl(fd, UI_SET_ABSBIT, ABS_TILT_Y);

    // Relative events
    ioctl(fd, UI_SET_EVBIT, EV_REL);
    ioctl(fd, UI_SET_RELBIT, REL_X);
    ioctl(fd, UI_SET_RELBIT, REL_Y);

    // Setting up absolute axes
    struct uinput_abs_setup abs_setup;

    // X axis setup
    memset(&abs_setup, 0, sizeof(abs_setup));
    abs_setup.code = ABS_X;
    abs_setup.absinfo.minimum = 0;
    abs_setup.absinfo.maximum = 4096;
    abs_setup.absinfo.resolution = 2540; // 100 DPI
    ioctl(fd, UI_ABS_SETUP, &abs_setup);

    // Y axis setup
    abs_setup.code = ABS_Y;
    abs_setup.absinfo.minimum = 0;
    abs_setup.absinfo.maximum = 4096;
    abs_setup.absinfo.resolution = 2540; // 100 DPI
    ioctl(fd, UI_ABS_SETUP, &abs_setup);

    // Pressure setup
    abs_setup.code = ABS_PRESSURE;
    abs_setup.absinfo.minimum = 0;
    abs_setup.absinfo.maximum = 1024;
    abs_setup.absinfo.resolution = 0; // Pressure resolution not defined
    ioctl(fd, UI_ABS_SETUP, &abs_setup);

    // X tilt setup
    abs_setup.code = ABS_TILT_X;
    abs_setup.absinfo.minimum = -90;
    abs_setup.absinfo.maximum = 90;
    abs_setup.absinfo.resolution = 1; // 1 unit per degree
    ioctl(fd, UI_ABS_SETUP, &abs_setup);

    // Y tilt setup
    abs_setup.code = ABS_TILT_Y;
    abs_setup.absinfo.minimum = -90;
    abs_setup.absinfo.maximum = 90;
    abs_setup.absinfo.resolution = 1; // 1 unit per degree
    ioctl(fd, UI_ABS_SETUP, &abs_setup);

    // Setting up uinput device (without absres setup)
    struct uinput_setup setup;
    memset(&setup, 0, sizeof(setup));
    snprintf(setup.name, UINPUT_MAX_NAME_SIZE, "%s", name);
    setup.id.bustype = BUS_USB;
    setup.id.vendor = 0x1984;
    setup.id.product = 0x2022;
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

JNIEXPORT jint JNICALL Java_ru_aloyenz_t501_driver_virtual_VPen_writePosition
        (JNIEnv *env, jclass clazz, jlong fd, jint x, jint y, jint pressure, jboolean touch, jint tiltX, jint tiltY,
        jboolean plusPressed, jboolean minusPressed) {
    if (fd <= 0) {
        return -1; // Invalid file descriptor
    }


    struct input_event ev;
    memset(&ev, 0, sizeof(ev));

    // Send tool pen event
    ev.type = EV_KEY;
    ev.code = BTN_TOOL_PEN;
    ev.value = 1; // Stylus always present
    if (write(fd, &ev, sizeof(ev)) < 0) {
        return -9; // Error writing tool pen event
    }

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

    // Write stylus button events
    ev.type = EV_KEY;
    ev.code = BTN_STYLUS;
    ev.value = plusPressed ? 1 : 0;
    if (write(fd, &ev, sizeof(ev)) < 0) {
        return -10; // Error writing plus button
    }

    ev.code = BTN_STYLUS2;
    ev.value = minusPressed ? 1 : 0;
    if (write(fd, &ev, sizeof(ev)) < 0) {
        return -11; // Error writing minus button
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

JNIEXPORT void JNICALL Java_ru_aloyenz_t501_driver_virtual_VPen_shutdown(
    JNIEnv *env, jclass class, jlong fdPointer) {
  if (fdPointer > 0) {
    // Destroy uinput device
    ioctl((int)fdPointer, UI_DEV_DESTROY);
    // Close
    close((int)fdPointer);
  }
}