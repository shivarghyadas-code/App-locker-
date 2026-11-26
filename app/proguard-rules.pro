# Manifest components are kept automatically.
# The map page calls MapActivity$Bridge.onPick() from JavaScript: keep its name.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
