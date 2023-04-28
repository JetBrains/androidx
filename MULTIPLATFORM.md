## General requirements
- Java 17 (should be specified in JAVA_HOME)
- [macOs/Linux] Android SDK downloaded via `./jbdeps/android-sdk/downloadAndroidSdk`
- [Windows] Android SDK downloaded from Android Studio and specified in ANDROID_SDK_ROOT

## Developing in IDE
1. Download Android Studio from [the official site](https://developer.android.com/studio/archive) (it is mandatory to use the version, written [here](https://github.com/JetBrains/androidx/blob/jb-main/gradle/libs.versions.toml#L11)). As an alternative you can use IDEA, which is compatible with [this AGP version](https://github.com/JetBrains/androidx/blob/jb-main/gradle/libs.versions.toml#L5), or you can disable Android plugin in IDEA plugins, to develop non-Android targets.
2. [macOs/Linux] Download Android SDK via `./jbdeps/android-sdk/downloadAndroidSdk`
3. [Windows] Download Android SDK via [Android Studio](https://developer.android.com/studio/intro/update#sdk-manager) and specify it in ANDROID_SDK_ROOT environment variable. Components, their versions and folder structure should be the same as in downloaded via script `./jbdeps/android-sdk/downloadAndroidSdk` SDK for other platforms.
4. Specify Gradle JVM to use JDK 17 in InteliJ IDEA Preferences (`Build, Execution, Deployment -> Build Tools -> Gradle`)

### Run tests

Run tests for Desktop:
```bash
./gradlew :mpp:testDesktop
```

Run tests for Web:
```bash
./gradlew :mpp:testWeb
```

Run tests for UIKit:
```bash
./gradlew :mpp:testUIKit
```

### Publishing
Compose Multiplatform core libraries can be published to local Maven with the following steps:
1. Set `COMPOSE_CUSTOM_VERSION` environment variable
```bash
export COMPOSE_CUSTOM_VERSION=0.0.0-custom-version
```
2. Publish core libraries
```bash
./gradlew :mpp:publishComposeJbToMavenLocal -Pcompose.platforms=all
```
`-Pcompose.platforms=all` could be replace with comma-separated list of platforms, such as `js,jvm,androidDebug,androidRelease,macosx64,uikit`.

3. Publish extended icons
```bash
./gradlew :mpp:publishComposeJbExtendedIconsToMavenLocal -Pcompose.platforms=all --max-workers=1
```

4. (Optional) Publish Gradle plugin using [instructions](https://github.com/JetBrains/compose-multiplatform/tree/master/compose#publishing) to check changes locally.

### Run samples
Run jvm desktop samples:
```bash
./gradlew :compose:mpp:demo:runDesktop
```
```bash
./gradlew :compose:desktop:desktop:desktop-samples:run1
```
```bash
./gradlew :compose:desktop:desktop:desktop-samples:run2
```
```bash
./gradlew :compose:desktop:desktop:desktop-samples:run3
```
```bash
./gradlew :compose:desktop:desktop:desktop-samples:runSwing
```
```bash
./gradlew :compose:desktop:desktop:desktop-samples:runWindowApi
```
```bash
./gradlew :compose:desktop:desktop:desktop-samples:runVsync
```
```bash
./gradlew :compose:desktop:desktop:desktop-samples:runLayout
```
```bash
./gradlew :compose:desktop:desktop:desktop-samples-material3:runScaffold
```

Run wasm sample:
```bash
./gradlew :compose:mpp:demo:jsRun
```

Run native macos X64 sample:
```bash
./gradlew :compose:mpp:demo:runDebugExecutableMacosX64
```

Run native macos Arm64 sample:
```bash
./gradlew :compose:mpp:demo:runDebugExecutableMacosArm64
```
