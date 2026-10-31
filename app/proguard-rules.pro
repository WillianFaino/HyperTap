# HyperTap ProGuard / R8 rules

# Keep Room generated files
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Keep models and entities for serialization
-keepclassmembers class com.hypertap.autoclicker.model.** { *; }
-keepclassmembers class com.hypertap.autoclicker.data.local.** { *; }

