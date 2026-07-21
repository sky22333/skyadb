-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
-renamesourcefileattribute SourceFile

-keep class com.sky22333.skyadb.AdbManagerApplication { <init>(); }
-keep class com.sky22333.skyadb.MainActivity { *; }

-keep class com.flyfishxu.kadb.** { *; }
-keep class com.flyfishxu.** { *; }
-dontwarn com.flyfishxu.**

-keep class io.github.rohitverma882.** { *; }
-dontwarn io.github.rohitverma882.**

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn timber.log.**

-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclasseswithmembernames class * {
    native <methods>;
}
