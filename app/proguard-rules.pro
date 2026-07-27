# 保留腾讯 X5 / TBS 相关类，防止被混淆导致内核加载失败
-keep class com.tencent.smtt.** { *; }
-keep class com.tencent.tbs.** { *; }
-dontwarn com.tencent.**

# WebView 相关保留
-keep class android.webkit.** { *; }
-keep class * extends android.webkit.WebView { *; }
-keep class * extends android.webkit.WebViewClient { *; }
-keep class * extends android.webkit.WebChromeClient { *; }
