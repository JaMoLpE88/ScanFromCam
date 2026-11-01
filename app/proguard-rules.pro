# OpenCV: JNI entry points are looked up by reflection from the Java classes.
-keep class org.opencv.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.opencv.**

# ML Kit loads model classes reflectively.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
