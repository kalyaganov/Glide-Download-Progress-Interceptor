# Glide Download Progress Interceptor

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Track image download progress when using [Glide](https://github.com/bumptech/glide) with [OkHttp](https://github.com/square/okhttp) on Android.

## Features

- **Lightweight** — a single OkHttp network interceptor that wraps response bodies
- **Thread-safe** listener management with synchronized collections
- **Lifecycle-aware** — optional AndroidX Lifecycle extension for automatic cleanup
- **No reflection** — pure OkHttp interceptor, no bytecode manipulation
- **Flexible** — provide your own `OkHttpClient` as a base for further customization

## How It Works

The library adds an OkHttp **network interceptor** that wraps every `ResponseBody` in a `ProgressResponseBody`. This wrapper replaces the body's `Source` with a `ForwardingSource` that counts bytes as they are read and calls back to registered `ProgressListener` instances.

```
┌─────────────┐     ┌──────────────────┐     ┌────────────────────┐
│  Glide      │────▶│  OkHttpClient    │────▶│  Network           │
│  Request    │     │  (with Progress  │     │                    │
│             │     │   Interceptor)   │     │                    │
└─────────────┘     └────────┬─────────┘     └────────────────────┘
                             │
                             │ ProgressResponseBody wraps
                             │ every response body
                             ▼
                    ┌──────────────────┐
                    │ ProgressListener │──▶ Your UI
                    │ onProgress(...)  │
                    └──────────────────┘
```

## Requirements

- Android API 21+
- Glide 4.16+
- OkHttp 4.12+
- Kotlin 1.9+

## Installation

Add the library to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ru.kalyaganov:glide-download-interceptor:2.0.1")
}
```

Or include the `glide-progress` module directly in your project.

## Usage

### 1. Register the OkHttp client with Glide

Create a `GlideModule` that replaces Glide's default HTTP client:

```kotlin
@GlideModule
class MyAppGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val client = GlideProgressManager.createProgressClient()
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(client)
        )
    }
}
```

### 2. Listen for progress

```kotlin
val manager = GlideProgressManager.getInstance()
val listener = ProgressListener { bytesRead, contentLength, done ->
    val percent = if (contentLength > 0) (bytesRead * 100 / contentLength) else 0
    Log.d("Progress", "$percent% ($bytesRead / $contentLength) done=$done")
}

manager.addListener(listener)

// Trigger a Glide download
Glide.with(context)
    .downloadOnly()
    .load("https://example.com/image.jpg")
    .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
    .get()

manager.removeListener(listener)
```

### 3. With Android Lifecycle (recommended)

Use the lifecycle-aware extension to avoid leaking listeners:

```kotlin
class MyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GlideProgressManager.getInstance().observeProgress(lifecycle) { bytesRead, contentLength, done ->
            // Safe to update UI — listener is auto-removed on destroy.
            binding.progressBar.progress = ((bytesRead * 100) / contentLength).toInt()
        }
    }
}
```

### 4. Custom OkHttpClient

Pass a pre-configured `OkHttpClient` as the base:

```kotlin
val baseClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

val progressClient = GlideProgressManager.createProgressClient(baseClient)
```

## API Reference

| Class | Description |
|---|---|
| [`ProgressListener`](glide-progress/src/main/java/ru/futurobot/glidedownloadinterceptor/ProgressListener.kt) | Functional interface with `onProgress(bytesRead, contentLength, done)` |
| [`GlideProgressManager`](glide-progress/src/main/java/ru/futurobot/glidedownloadinterceptor/GlideProgressManager.kt) | Main entry point. Manages listeners and creates the progress-tracking OkHttp client |
| [`ProgressResponseBody`](glide-progress/src/main/java/ru/futurobot/glidedownloadinterceptor/ProgressResponseBody.kt) | OkHttp `ResponseBody` wrapper that counts bytes read |
| [`ProgressInterceptor`](glide-progress/src/main/java/ru/futurobot/glidedownloadinterceptor/ProgressInterceptor.kt) | OkHttp `Interceptor` that wraps responses for progress tracking |

## Sample App

The `app/` module contains a complete demo application showcasing:

- Download progress with percentage and byte counts
- Image preview after download
- Material 3 UI
- Both "download only" and "load into view" modes

## License

MIT — see [LICENSE](LICENSE) for details.
