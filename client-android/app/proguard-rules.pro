# Add project specific ProGuard rules here.

# Protobuf
-keepclassmembers class com.im.protocol.** {
  *** newInstance(...);
  *** parseFrom(...);
}
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
