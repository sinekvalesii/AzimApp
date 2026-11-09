# WebView köprüsü: JavaScript'ten çağrılan metotlar küçültmede silinmemeli.
-keepattributes JavascriptInterface
-keepclassmembers class com.selcuk.azim.AzimBridge {
    @android.webkit.JavascriptInterface <methods>;
}
