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
