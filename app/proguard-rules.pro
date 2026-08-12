# Room
-keepattributes *Annotation*
-keep class com.jiyixia.app.data.entity.** { *; }
-keep class com.jiyixia.app.data.dao.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** INSTANCE;
    public static ** DAO;
}
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# 讯飞 MSC SDK
-keep class com.iflytek.** { *; }
-dontwarn com.iflytek.**
-keepattributes Signature
-keepattributes *Annotation*
