# Glide Download Progress Interceptor — Modernization Specification

## 1. Executive Summary

Modernize this 2015-era Android demo into a well-tested, documented, and reusable library that tracks Glide image download progress via OkHttp interceptors. The core technique (wrapping `ResponseBody` with a byte-counting `ForwardingSource`) remains sound, but everything around it is outdated.

---

## 2. Fix Typos and Naming Errors

### 2.1 Package Name Typo
| Current | Corrected |
|---|---|
| `ru.futurobot.glidedownloadintercenptor` | `ru.futurobot.glidedownloadinterceptor` |

The word "interceptor" is missing the second 'r' in the package name, manifest metadata, and all `import` statements.

### 2.2 Build File Typo
`build.gradle` → rename to `build.gradle.kts` (Kotlin DSL) or keep as `.gradle` but fix content.

### 2.3 Resource Strings
Add proper string resources for all UI text (currently "Start downloading" is hardcoded in XML).

---

## 3. Build System Upgrade

### 3.1 Gradle Wrapper
| Current | Target |
|---|---|
| Gradle 2.8 | Gradle 8.7+ |

### 3.2 Android Gradle Plugin
| Current | Target |
|---|---|
| AGP 2.0.0-alpha2 | AGP 8.4+ |

### 3.3 Build Configuration
- Convert `build.gradle` files to **Kotlin DSL** (`build.gradle.kts`)
- Add `gradle.properties` settings:
  ```properties
  org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
  android.useAndroidX=true
  android.nonTransitiveRClass=true
  ```
- Add `libs.versions.toml` version catalog for clean dependency management

### 3.4 Module Structure
Restructure into two modules:
```
├── app/                    # Demo app (sample usage)
├── glide-progress/         # Library module (reusable)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
└── README.md
```

---

## 4. Dependency Updates

### 4.1 Core Dependencies
| Dependency | Current Version | Target Version |
|---|---|---|
| OkHttp | 2.6.0 (`com.squareup.okhttp`) | 4.12.0 (`com.squareup.okhttp3`) |
| Glide | 3.6.1 | 4.16.0 |
| Glide OkHttp Integration | 1.3.1 | 4.16.0 (bundled in Glide 4.x) |
| AndroidX AppCompat | (support-v7:23.1.1) | 1.7.0 |
| compileSdk | 23 | 34 |
| minSdk | 10 | 21 |
| targetSdk | 23 | 34 |

### 4.2 Test Dependencies (New)
```toml
junit = "4.13.2"
mockito-core = "5.12.0"
mockito-kotlin = "5.4.0"
robolectric = "4.12.2"
okhttp-mockwebserver = "4.12.0"
androidx-test-core = "1.6.1"
androidx-test-runner = "1.6.1"
```

---

## 5. Code Modernization — Library Module (`glide-progress/`)

### 5.1 Package Structure
```
ru.futurobot.glidedownloadinterceptor/
├── ProgressListener.kt           # (was .java) Interface
├── ProgressResponseBody.kt       # (was .java) OkHttp ResponseBody wrapper
└── GlideProgressManager.kt       # (was GlideProgressListener.java) Core manager
```

### 5.2 Convert to Kotlin
All library code should be written in Kotlin for conciseness, null-safety, and coroutine support.

### 5.3 `ProgressListener` Interface
```kotlin
fun interface ProgressListener {
    fun onProgress(bytesRead: Long, contentLength: Long, done: Boolean)
}
```
Changes:
- Rename `update` → `onProgress` (more idiomatic)
- Use `fun interface` (SAM conversion) for Kotlin

### 5.4 `ProgressResponseBody`
- Update from OkHttp 2.x `com.squareup.okhttp.ResponseBody` → OkHttp 4.x `okhttp3.ResponseBody`
- `BufferedSource`, `Buffer`, `ForwardingSource`, `Okio` move from `okio` to `okio` (same library, just newer)
- `MediaType` → `okhttp3.MediaType`
- Use `@Throws(IOException::class)` annotations for Java interop
- Add Javadoc/KDoc on all public methods

### 5.5 `GlideProgressManager` (was `GlideProgressListener`)
Major refactor — this is the most problematic file:

**Current problems:**
- Static mutable global state (not thread-safe enough, not testable)
- `WeakReference` list can leak if not cleaned properly
- The interceptor is added to `networkInterceptors()` — should be `addNetworkInterceptor()` in OkHttp 4.x
- Singleton OkHttpClient — no way to customize
- Listener management is fragile (manual add/remove with WeakReference find)

**New design:**
```kotlin
class GlideProgressManager private constructor(
    private val listeners: MutableSet<ProgressListener> = LinkedHashSet()
) {
    companion object {
        @Volatile
        private var INSTANCE: GlideProgressManager? = null

        fun getInstance(): GlideProgressManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: GlideProgressManager().also { INSTANCE = it }
            }

        /**
         * Creates a configured OkHttpClient with progress interception.
         * Callers can use this as a base and further customize.
         */
        @JvmStatic
        fun createProgressClient(baseClient: OkHttpClient = OkHttpClient()): OkHttpClient {
            val manager = getInstance()
            return baseClient.newBuilder()
                .addNetworkInterceptor(ProgressInterceptor(manager))
                .build()
        }
    }

    fun addListener(listener: ProgressListener) { ... }
    fun removeListener(listener: ProgressListener) { ... }
    fun clearListeners() { ... }

    internal fun notifyProgress(bytesRead: Long, contentLength: Long, done: Boolean) { ... }
}

internal class ProgressInterceptor(
    private val manager: GlideProgressManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalResponse = chain.proceed(chain.request())
        val body = originalResponse.body ?: return originalResponse
        return originalResponse.newBuilder()
            .body(ProgressResponseBody(body) { bytesRead, contentLength, done ->
                manager.notifyProgress(bytesRead, contentLength, done)
            })
            .build()
    }
}
```

**Key improvements:**
- Thread-safe listener set using `synchronized` or `ConcurrentHashMap.newKeySet()`
- Separate `ProgressInterceptor` as its own class (testable independently)
- Factory method `createProgressClient()` lets consumers provide a base `OkHttpClient`
- Drop `WeakReference` wrapper — callers are responsible for removeListener; add lifecycle-aware extension for Android
- Add `clearListeners()` for cleanup
- Proper KDoc on every public member

### 5.6 Android Lifecycle Integration (New)
Add an extension function for automatic listener cleanup:

```kotlin
fun GlideProgressManager.observeProgress(
    lifecycle: Lifecycle,
    listener: ProgressListener
) {
    addListener(listener)
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            removeListener(listener)
        }
    })
}
```

---

## 6. Code Modernization — App Module (`app/`)

### 6.1 `MainActivity` Rewrite
| Current | Replacement |
|---|---|
| `AsyncTask` | Kotlin coroutines (`lifecycleScope.launch`) |
| `AppCompatActivity` | Keep, but with ViewBinding |
| Hardcoded GIF URL | Move to `strings.xml` or constants |
| Manual findViewById | ViewBinding or DataBinding |

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val progressManager = GlideProgressManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.downloadButton.setOnClickListener { startDownload() }
    }

    private fun startDownload() {
        val listener = ProgressListener { bytesRead, contentLength, done ->
            binding.progressBar.max = 100
            binding.progressBar.progress = if (contentLength > 0)
                ((100L * bytesRead) / contentLength).toInt() else 0
            binding.statusText.text = if (done) "Done!" else "Downloading..."
        }

        lifecycleScope.launch(Dispatchers.IO) {
            progressManager.addListener(listener)
            try {
                Glide.with(this@MainActivity)
                    .downloadOnly()
                    .load(TEST_IMAGE_URL)
                    .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                    .get()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.statusText.text = "Error: ${e.message}"
                }
            } finally {
                progressManager.removeListener(listener)
            }
        }
    }
}
```

### 6.2 `MainApplication` — Simplify
With Glide 4.x and the manifest meta-data approach:
```kotlin
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Glide 4.x auto-discovers AppGlideModule via annotation processor
        // Fallback manual registration is no longer needed
    }
}
```

### 6.3 `MyGlideModule` → `MyAppGlideModule`
Glide 4.x uses `AppGlideModule` instead of `GlideModule`:
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

---

## 7. UI Redesign

### 7.1 Current UI
- Simple `RelativeLayout` with centered `LinearLayout`
- Ring-shaped `ProgressBar` (horizontal style with ring drawable)
- Single button

### 7.2 Proposed UI (Material 3)

```
┌──────────────────────────────┐
│                              │
│         ┌──────────┐         │
│         │  Image    │         │
│         │ Preview   │         │
│         │ (loaded   │         │
│         │  image)   │         │
│         └──────────┘         │
│                              │
│     ═══════════════════      │   ← LinearProgressIndicator
│        42% (1.2 / 3.1 MB)    │   ← Text showing progress
│                              │
│   ┌────────────────────┐     │
│   │  DOWNLOAD IMAGE     │     │   ← MaterialButton
│   └────────────────────┘     │
│                              │
│   ┌────────────────────┐     │
│   │  LOAD INTO VIEW     │     │   ← MaterialButton (outlined)
│   └────────────────────┘     │
│                              │
│   Status: Idle               │   ← Helper text
└──────────────────────────────┘
```

**Key changes:**
- **Material 3** theming (`MaterialComponents` theme)
- **`LinearProgressIndicator`** with determinate mode (replaces ring ProgressBar)
- **`MaterialButton`** for buttons
- **`ImageView`** to show loaded image after download
- **Status text** showing bytes downloaded / total size and percentage
- **Two modes**: "Download Only" (just track progress) and "Load into View" (show the image)
- Use `ConstraintLayout` instead of nested `RelativeLayout`/`LinearLayout`

### 7.3 Theme
Update `styles.xml` to Material 3:
```xml
<style name="Theme.GlideProgress" parent="Theme.Material3.Light.NoActionBar">
    <item name="colorPrimary">@color/md_theme_primary</item>
    ...
</style>
```

Add Material 3 color scheme via `material-theme-builder` or manual XML.

---

## 8. Testing Strategy

### 8.1 Unit Tests (library module — `glide-progress/src/test/`)

#### `ProgressResponseBodyTest`
- **Given** a mock `ResponseBody` with known content
- **When** the source is read completely
- **Then** the `ProgressListener` receives correct `bytesRead`, `contentLength`, and `done=true` at the end
- Test partial reads (simulate network chunking)
- Test zero-length body
- Test exception during read (source throws `IOException`)

#### `ProgressInterceptorTest`
- Use OkHttp's `MockWebServer` to serve a known response
- Build an `OkHttpClient` with `ProgressInterceptor` and a test listener
- Verify the listener receives progress callbacks
- Verify the response body is still fully readable after interception

#### `GlideProgressManagerTest`
- **Add/Remove listeners**: Add 3 listeners, notify progress, verify all 3 received; remove 1, notify, verify only 2 received
- **Thread safety**: Launch 10 coroutines each adding/removing listeners concurrently, verify no `ConcurrentModificationException`
- **Clear all**: `clearListeners()` should result in no callbacks
- **Duplicate add**: Adding the same listener twice should not double-notify
- **Notify with no listeners**: should not crash

### 8.2 Instrumentation Tests (app module — `app/src/androidTest/`)
- Use `ActivityScenario` to launch `MainActivity`
- Verify UI elements exist
- Provide a `MockWebServer` URL to Glide, verify progress bar updates
- Test download failure (404, timeout) shows error state

### 8.3 Test File Structure
```
glide-progress/
└── src/
    ├── test/
    │   └── java/ru/futurobot/glidedownloadinterceptor/
    │       ├── ProgressResponseBodyTest.kt
    │       ├── ProgressInterceptorTest.kt
    │       └── GlideProgressManagerTest.kt
    └── androidTest/
        └── java/ru/futurobot/glidedownloadinterceptor/
            └── GlideProgressManagerInstrumentedTest.kt

app/
└── src/
    ├── test/
    │   └── java/ru/futurobot/glidedownloadinterceptor/
    │       └── MainActivityViewModelTest.kt
    └── androidTest/
        └── java/ru/futurobot/glidedownloadinterceptor/
            └── MainActivityTest.kt
```

---

## 9. Documentation

### 9.1 `README.md`
Create a comprehensive README with:

```markdown
# Glide Download Progress Interceptor

[![Maven Central](https://img.shields.io/maven-central/v/...)](...)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](...)

Track download progress for images loaded with Glide + OkHttp.

## Features
- Lightweight OkHttp interceptor for Glide download progress
- Thread-safe listener management
- Lifecycle-aware extensions for Android
- No reflection, no bytecode manipulation — pure OkHttp interceptor

## Installation
### Gradle (Kotlin DSL)
...

## Usage
### Basic
...

### With Android Lifecycle
...

### Custom OkHttpClient
...

## How It Works
[Architecture diagram / explanation]

## API Reference
| Class | Description |
|---|---|
| `ProgressListener` | Functional interface for progress callbacks |
| `GlideProgressManager` | Main entry point — manages listeners and creates the OkHttp client |
| `ProgressResponseBody` | OkHttp ResponseBody wrapper that counts bytes |

## Sample App
See the `app/` module for a complete demo.

## Requirements
- Android API 21+
- Glide 4.16+
- OkHttp 4.12+
- Kotlin 1.9+

## License
MIT
```

### 9.2 `CHANGELOG.md`
```markdown
# Changelog

## [2.0.0] - unreleased
### Changed
- Complete rewrite in Kotlin
- Updated to OkHttp 4.x and Glide 4.x
- New `GlideProgressManager` API (replaces `GlideProgressListener`)
- Package renamed: fixed typo "intercenptor" → "interceptor"

### Added
- Unit tests
- Integration tests
- Lifecycle-aware listener management
- README and API documentation
- Sample app with Material 3 UI

### Removed
- `AsyncTask` usage (replaced with coroutines)
- `WeakReference` listener storage (replaced with explicit lifecycle management)
- Support library dependencies (migrated to AndroidX)
```

### 9.3 `LICENSE`
Add MIT license file.

### 9.4 KDoc/Javadoc
Every public class and method must have KDoc:
```kotlin
/**
 * A functional interface for receiving download progress updates.
 *
 * Implementations are notified on every chunk of data read from the network.
 *
 * @sample ru.futurobot.glidedownloadinterceptor.sample.main
 */
fun interface ProgressListener {
    /**
     * Called when download progress changes.
     *
     * @param bytesRead Total bytes read so far
     * @param contentLength Total content length (may be -1 if unknown)
     * @param done True if the download is complete
     */
    fun onProgress(bytesRead: Long, contentLength: Long, done: Boolean)
}
```

---

## 10. Error Handling

### 10.1 Current Issues
- `doInBackground` silently catches `InterruptedException` and `ExecutionException` with just `printStackTrace()`
- No user-visible error feedback
- No handling of unknown `contentLength` (-1)
- `ProgressBar` division by zero if `contentLength` is 0

### 10.2 Improvements
- Wrap Glide download in try/catch and propagate errors to UI via `withContext(Dispatchers.Main)`
- Handle `contentLength == -1` by showing indeterminate progress
- Handle `contentLength == 0` gracefully (no division)
- Add timeout handling
- Add retry capability (optional)

---

## 11. Implementation Order

| Phase | Task | Effort |
|---|---|---|
| **Phase 1** | Fix typos, rename packages | 15 min |
| **Phase 2** | Upgrade Gradle/AGP, add version catalog | 30 min |
| **Phase 3** | Convert to Kotlin, restructure modules | 1 hour |
| **Phase 4** | Rewrite `GlideProgressManager` with new API | 1 hour |
| **Phase 5** | Rewrite `MainActivity` with coroutines + ViewBinding | 30 min |
| **Phase 6** | Redesign UI (Material 3) | 1 hour |
| **Phase 7** | Write unit tests for library module | 1.5 hours |
| **Phase 8** | Write instrumented tests | 1 hour |
| **Phase 9** | Write README, CHANGELOG, LICENSE, KDoc | 1 hour |
| **Phase 10** | Final review and cleanup | 30 min |

---

## 12. Risk Assessment

| Risk | Mitigation |
|---|---|
| OkHttp 4.x API differences | OkHttp 4.x is well-documented; the interceptor pattern is identical across 3.x and 4.x |
| Glide 4.x `AppGlideModule` vs `GlideModule` | Glide 4 uses annotation processor for module discovery; the `@GlideModule` annotation on `AppGlideModule` is the replacement |
| Kotlin migration introducing bugs | Write tests before/alongside migration; use `@JvmStatic`, `@JvmOverloads` for Java consumers |
| `AsyncTask` → Coroutines | Well-understood pattern; `lifecycleScope` is standard |
| Material 3 theme compatibility | `MaterialComponents` theme extends `AppCompat`; migration is straightforward |

---

## 13. Open Questions

1. **Should the library module be published to Maven Central?** If yes, add `maven-publish` plugin configuration.
2. **Should we support Java consumers?** Yes — annotate Kotlin code with `@JvmStatic`, `@JvmOverloads`, and `@JvmName` where needed.
3. **Should we support non-Android OkHttp usage?** The core interceptor (`ProgressResponseBody`, `ProgressInterceptor`) is pure OkHttp/Kotlin — it could be extracted into a non-Android module.
4. **Min SDK?** Proposed 21. If we need lower, we need to avoid `java.util.function` and API 24+ APIs. For now, 21 is reasonable (covers 99%+ of devices as of 2024).
