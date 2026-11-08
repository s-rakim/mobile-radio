# kotlinx.serialization: keep generated serializers for our models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.srakim.spitify.** {
    *** Companion;
}
-keepclasseswithmembers class com.srakim.spitify.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.srakim.spitify.**$$serializer { *; }

# Annotation-only dependencies pulled in by Guava (via Media3) and OkHttp.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
