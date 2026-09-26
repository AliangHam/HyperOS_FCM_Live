-dontwarn io.github.libxposed.annotation.**
-dontwarn androidx.**
-dontwarn com.google.android.material.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# Module app + hooks (reflection, layout inflation, libxposed)
-keep class io.github.howard20181.hyperos.fcmlive.** { *; }

# SwipeRefreshLayout is inflated from XML by fully-qualified name — must keep.
-keep class androidx.swiperefreshlayout.** { *; }
-keep class * extends androidx.swiperefreshlayout.widget.SwipeRefreshLayout { *; }

# Material3 widgets inflated from XML / styled via reflection.
-keep class com.google.android.material.** { *; }

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,MethodParameters
-keepattributes SourceFile,LineNumberTable
