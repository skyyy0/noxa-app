# NOXA R8 rules (release minify enabled in v1.16).
#
# Stack traces stay readable: the crash log is attached to support reports,
# so file/line info survives obfuscation (mapping.txt is archived by CI).

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- Release hygiene --------------------------------------------------
# Verbose/debug logs never ship: they can leak stream URLs, resolver
# responses and account emails into logcat on user devices. Info/warning/
# error stay — they back the honest diagnostics (ShareCard, resolver,
# crash paths) without the chatter.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# --- Moshi -------------------------------------------------------------
# Adapters are code-generated (KSP) and looked up by the generated factory
# names; data classes keep their @Json members.
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.example.data.**.*JsonAdapter { *; }
-dontwarn com.squareup.moshi.**
-dontwarn okio.**

# --- OkHttp / Retrofit -------------------------------------------------
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- On-device YouTube extraction (NewPipeExtractor) -------------------
# Metadata parsing uses string constants and a couple of reflective lookups;
# keeping the package intact is cheaper than chasing a runtime NoSuchMethod
# in the player.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.schabi.newpipe.extractor.**.* { *; }
-dontwarn org.schabi.newpipe.**
# NewPipe pulls in a few optional/Android-internal APIs that only exist on
# some devices.
-dontwarn org.jsoup.**
-dontwarn com.google.gson.**

# NewPipeExtractor bundles Rhino (`org.mozilla:rhino` + rhino-engine) for its
# optional script engine. Rhino's factory/converters reference desktop-Java
# APIs that never exist on Android (`java.beans.*`, `javax.script.*`) and are
# never invoked there — R8 correctly warns, and these classes are genuinely
# absent by design, not accidentally missing from our dependency set.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**
-dontwarn java.awt.**
-dontwarn javax.lang.model.**
-dontwarn java.nio.file.**

# --- Firebase (AI, Auth, Firestore, App Check) -------------------------
# Official consumer rules ship with the SDKs; these only silence optional
# pieces absent from our dependency set.
-dontwarn com.google.ai.**
-dontwarn com.google.android.gms.**
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# R8 FULL MODE (v3.5.5) — "login must never break" section.
#
# Full mode assumes nothing is reached reflectively unless a keep rule says
# so. Firebase Auth, Google Sign-In and Credentials all resolve provider
# classes, Parcelables and callbacks by name at runtime, so every one of
# those packages is kept whole on purpose. The APK cost is a few MB; a broken
# Google login is not an acceptable trade for it.
# ---------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.protobuf.** { *; }
# Credentials membawa protobuf-javalite, sementara kelas bersama Firebase
# (JavaFeaturesProto) mereferensikan kelas protobuf penuh yang tidak ikut
# dikemas — referensi itu hanya disentuh tooling descriptor, bukan alur login.
-dontwarn com.google.protobuf.**
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase reads provider ids / scopes from annotations and instantiates
# activities named in google-services.json — attributes must survive.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers @com.google.firebase.firestore.PropertyName class * { *; }

# Room: the generated implementation is looked up through the database class
# name; entities are read by generated code, not by reflection, but the DAO
# implementations are instantiated by class name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Retrofit/OkHttp service interfaces are proxied reflectively.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation interface * extends retrofit2.Call
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Enums persisted by name (PerfProfile.Tier, DSPSource, dst.) break when R8
# renames the constants — SharedPreferences would fail valueOf() after update.
-keepclassmembers enum * { *; }

# --- App code: only what genuinely has to survive -----------------------
# Everything else under com.example.* is deliberately renamed, shrunk and
# repackaged. The APK is public; the repository is the readable copy.
#
# Room instantiates the generated database implementation by class name, so
# the database hierarchy keeps its names. Entities and DAOs are called
# directly from generated code and need no keep rule of their own.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Moshi resolves code-generated adapters with Class.forName("<pkg>.<Dto>JsonAdapter").
-keepnames class **JsonAdapter

# Compose rememberSaveable / DevPanel state objects are restored by shape.
-keep class com.example.ui.screens.DevUiState** { *; }
-dontwarn org.conscrypt.**

# --- Credentials / Google ID ------------------------------------------
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.**

# --- Compose / AndroidX -----------------------------------------------
-dontwarn androidx.compose.**
-dontwarn androidx.media3.**

# Kotlin metadata is read reflectively by Moshi/KSP codegen and by
# kotlinx.coroutines' internal lookup.
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# BuildConfig dibaca lewat refleksi (`Secrets.get(name)`), jadi field-nya
# TIDAK boleh dihapus/di-rename R8 full mode — kalau hilang, key opsional
# (YouTube, resolver) dan override FREEMODEL_API_KEY diam-diam jadi kosong.
-keep class com.example.BuildConfig { *; }
-keepclassmembers class **.BuildConfig {
    public static <fields>;
}

# Flatten our own packages. Without this the package names alone
# (com.example.data.remote, .auth, .ai) still tell a reader where the
# interesting code lives, even when every class inside is renamed.
-allowaccessmodification
-repackageclasses ''
