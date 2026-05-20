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
