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

# Material widgets inflated from XML by fully-qualified name. Everything else in
# the library (datepicker, timepicker, carousel, ...) is unreferenced and now
# shrinks away; code-only entry points (MaterialAlertDialogBuilder, DynamicColors)
# stay via ordinary R8 reachability.
-keep class com.google.android.material.materialswitch.MaterialSwitch { *; }
-keep class com.google.android.material.card.MaterialCardView { *; }
-keep class com.google.android.material.loadingindicator.LoadingIndicator { *; }
-keep class com.google.android.material.floatingactionbutton.FloatingActionButton { *; }

# Jetpack Compose is only reached from PrivacyScreen (kept above), so plain
# reachability is enough — no blanket keep. Keep the lint silence for optional
# tooling references.
-dontwarn androidx.compose.**

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,MethodParameters
-keepattributes SourceFile,LineNumberTable
