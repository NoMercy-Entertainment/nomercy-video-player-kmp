# JNA binds by NAME, so R8 renaming this interface's methods severs it from the
# C symbols in libass.so and every call fails with nothing thrown. Measured on a
# real phone: zero `ass_*` strings survived in the shipped dex while the 2.4MB
# libass.so sat beside them, and the viewer saw no styled subtitle at all while
# the same code drew correctly in an unminified build.
#
# Consumer rules rather than the app's own, because this is the library's
# contract with R8: any consumer that minifies would meet the same wall, and one
# of them finding it again is one too many.
-keep interface tv.nomercy.player.video.ass.LibAss { *; }

# Structure maps FIELDS by name and order, both read reflectively.
-keep class tv.nomercy.player.video.ass.AssImageStruct { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    <fields>;
    *** getFieldOrder();
}

-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Library { *; }
-keepclassmembers class * implements com.sun.jna.Library { *; }
-dontwarn java.awt.**
