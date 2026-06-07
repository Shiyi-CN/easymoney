# Room
-keepattributes *Annotation*
-keep class com.jiyixia.app.data.entity.** { *; }
-keep class com.jiyixia.app.data.dao.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# Compose
-dontwarn androidx.compose.**

# SherpaOnnx (离线语音识别 - JNI native 调用)
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**
