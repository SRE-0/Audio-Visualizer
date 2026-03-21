# Keep all public API classes of the library
-keep public class com.sre404.audiovisualizer.** { public *; }

# Keep native method bindings
-keepclasseswithmembernames class * {
    native <methods>;
}