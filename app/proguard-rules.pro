# kotlinx.serialization (backup format)
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.moddingforge.ezplanner.**$$serializer { *; }
-keepclassmembers class com.moddingforge.ezplanner.** {
    *** Companion;
}
-keepclasseswithmembers class com.moddingforge.ezplanner.** {
    kotlinx.serialization.KSerializer serializer(...);
}
