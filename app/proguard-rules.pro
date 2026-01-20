# Add project specific ProGuard rules here.
# You can use the generated configuration.txt contents to check for missing rules.

# Hilt
-keep class com.lomo.app.LomoApplication_HiltComponents { *; }
-keep class com.lomo.app.di.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase$Builder
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class * { *; }

# Data Classes (Serialization often needs them, though we rely on Room/Gson)
-keepclassmembers class com.lomo.domain.model.** { <fields>; }
-keepclassmembers class com.lomo.data.local.entity.** { <fields>; }

# Generic Compose Rules (Usually handled by R8 automatically but safe to add)
# Generic Compose Rules (Handled by R8 automatically)


# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Fix for R8 missing kotlin.time classes referenced by kotlinx-serialization
-dontwarn kotlin.time.**
-keep class kotlin.time.** { *; }
