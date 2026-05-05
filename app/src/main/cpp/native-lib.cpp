#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_notificationarchiver_MainActivity_formatNotificationText(
        JNIEnv* env,
        jobject /* this */,
        jstring packageName,
        jstring title,
        jstring text) {

    const char *nativePackage = env->GetStringUTFChars(packageName, nullptr);
    const char *nativeTitle = env->GetStringUTFChars(title, nullptr);
    const char *nativeText = env->GetStringUTFChars(text, nullptr);

    std::string formatted = "Приложение: ";
    formatted += nativePackage;
    formatted += "\nЗаголовок: ";
    formatted += nativeTitle;
    formatted += "\nТекст: ";
    formatted += nativeText;

    env->ReleaseStringUTFChars(packageName, nativePackage);
    env->ReleaseStringUTFChars(title, nativeTitle);
    env->ReleaseStringUTFChars(text, nativeText);

    return env->NewStringUTF(formatted.c_str());
}