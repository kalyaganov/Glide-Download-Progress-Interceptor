# Changelog

All notable changes to this project will be documented in this file.

## [2.0.0] — 2026-07-05

### Added
- Complete rewrite in Kotlin
- Thread-safe `GlideProgressManager` with proper singleton pattern
- `ProgressInterceptor` as a separate, testable class
- Lifecycle-aware `observeProgress()` extension for AndroidX Lifecycle
- Factory method `createProgressClient()` for custom `OkHttpClient` configuration
- Comprehensive unit tests (`ProgressResponseBodyTest`, `ProgressInterceptorTest`, `GlideProgressManagerTest`)
- Instrumented tests for the demo app
- `README.md` with usage documentation and API reference
- `CHANGELOG.md` and `LICENSE` files
- Material 3 UI redesign for the demo app
- KDoc on all public classes and methods
- GitHub Actions CI (unit tests, lint, assemble)
- Maven Central publishing via `com.vanniktech.maven.publish`

### Changed
- **Namespace renamed**: `ru.futurobot` → `ru.kalyaganov` (correct domain)
- **Package renamed**: fixed typo `glidedownloadintercenptor` → `glidedownloadinterceptor`
- Updated OkHttp from 2.6.0 to 4.12.0
- Updated Glide from 3.6.1 to 4.16.0
- Updated Android Gradle Plugin from 2.0.0-alpha2 to 8.4.2
- Updated Gradle wrapper from 2.8 to 8.7
- Migrated from Support Library to AndroidX
- `minSdk` raised from 10 to 21
- `compileSdk` / `targetSdk` updated from 23 to 34
- `ProgressListener.update()` renamed to `onProgress()` and converted to `fun interface`
- `GlideProgressListener` renamed to `GlideProgressManager` with redesigned API
- `MainActivity` rewritten to use coroutines instead of `AsyncTask`
- `MyGlideModule` updated from `GlideModule` to `AppGlideModule` with `@GlideModule` annotation
- Build files converted to Kotlin DSL with version catalog (`libs.versions.toml`)
- Project split into `glide-progress` (library) and `app` (demo) modules

### Removed
- `WeakReference`-based listener storage (replaced with explicit add/remove + lifecycle extension)
- Static mutable global state in `GlideProgressListener`
- Support Library dependencies
- Ring-shaped `ProgressBar` (replaced with `LinearProgressIndicator`)
- Hardcoded GIF URL (replaced with constant and string resources)

## [1.0.0] — 2015-12-06

- Initial release
- Basic OkHttp 2.x interceptor for Glide 3.x download progress
- `GlideProgressListener` singleton with `WeakReference` listener list
- `ProgressResponseBody` wrapping `ForwardingSource`
- Demo app with `AsyncTask` and ring `ProgressBar`
