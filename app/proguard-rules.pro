# Room supplies consumer keep rules for generated database implementations.
# Keep AccessibilityService entry points referenced from the manifest.
-keep class com.focusgate.app.service.AccessibilityMonitorService { *; }

# Keep enum names because historical JSON and encrypted migration archives store them by name.
-keepclassmembers enum com.focusgate.app.data.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.focusgate.app.rule.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
