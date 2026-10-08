# ProGuard rules for Petly Android app

# Retain generic type information for Moshi/Retrofit
-keepattributes Signature

# Retain annotations for Retrofit
-keepattributes *Annotation*

# Keep Moshi generated adapters
-keep class com.squareup.moshi.** { *; }

# Keep OkHttp classes
-keep class okhttp3.** { *; }

# Keep Retrofit classes
-keep class retrofit2.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep public classes and their public/protected members
-keep public class * {
    public protected *;
}

# Keep names of classes implementing Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}