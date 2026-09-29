# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.lockin.focus.** {
    *** Companion;
}
-keepclasseswithmembers class com.lockin.focus.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lockin.focus.**$$serializer { *; }
