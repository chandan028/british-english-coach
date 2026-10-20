# LiteRT-LM uses JNI; keep its classes and native-facing members.
-keep class com.google.ai.edge.litertlm.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.britishcoach.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.britishcoach.**$$serializer { *; }
