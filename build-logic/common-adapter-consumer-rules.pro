# Keep adapter class names intact so they can be found by CloudX SDK.
-keep class kotlin.Metadata
-keep class * implements io.cloudx.sdk.internal.adapter.* {
    public static *;
}