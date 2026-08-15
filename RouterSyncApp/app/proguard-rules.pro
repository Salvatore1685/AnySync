# jcifs-ng usa reflection per alcune classi interne
-keep class jcifs.** { *; }
-dontwarn jcifs.**

# commons-net
-dontwarn org.apache.commons.net.**

# sardine-android (basata su OkHttp)
-dontwarn com.thegrizzlylabs.sardineandroid.**
-dontwarn okhttp3.**
-dontwarn okio.**
