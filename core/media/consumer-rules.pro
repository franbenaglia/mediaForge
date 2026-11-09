# youtubedl-android deserializa con Jackson por reflexión.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

# youtubedl-android descomprime Python y FFmpeg con Commons Compress, que crea las clases de
# campos extra de ZIP por reflexión (ExtraFieldUtils.register): sin esto, R8 las vuelve abstractas
# y el primer arranque falla con "is not a concrete class" (T11.1).
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-dontwarn org.apache.commons.compress.**
