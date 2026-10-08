# Reglas de R8/ProGuard. La compilacion release no aplica minificacion por
# ahora (isMinifyEnabled = false), pero se dejan las reglas necesarias para
# kotlinx.serialization por si se activa mas adelante.

# kotlinx.serialization: conservar los serializadores generados.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers, allowshrinking class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep, includedescriptorclasses class com.gmini.android.**$$serializer { *; }
-keepclassmembers class com.gmini.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.gmini.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio: dependencias opcionales que no se usan en runtime.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
