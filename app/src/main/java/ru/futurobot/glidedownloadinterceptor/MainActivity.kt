package ru.futurobot.glidedownloadinterceptor

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.futurobot.glidedownloadinterceptor.databinding.ActivityMainBinding
import ru.futurobot.glidedownloadinterceptor.ext.observeProgress
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val progressManager = GlideProgressManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.downloadButton.setOnClickListener { startDownload() }
        binding.loadIntoViewButton.setOnClickListener { loadIntoView() }
    }

    private fun startDownload() {
        setUiState(UiState.Loading)

        lifecycleScope.launch(Dispatchers.IO) {
            val listener = createProgressListener()
            progressManager.addListener(listener)

            try {
                Glide.with(this@MainActivity)
                    .downloadOnly()
                    .load(TEST_IMAGE_URL)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                    .get()

                withContext(Dispatchers.Main) {
                    setUiState(UiState.Done("Download complete!"))
                }
            } catch (e: SocketTimeoutException) {
                withContext(Dispatchers.Main) {
                    setUiState(UiState.Error("Request timed out. Check your connection."))
                }
            } catch (e: UnknownHostException) {
                withContext(Dispatchers.Main) {
                    setUiState(UiState.Error("No network connection."))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setUiState(UiState.Error("Error: ${e.message}"))
                }
            } finally {
                progressManager.removeListener(listener)
            }
        }
    }

    private fun loadIntoView() {
        setUiState(UiState.Loading)

        progressManager.observeProgress(lifecycle) { bytesRead, contentLength, _ ->
            updateProgress(bytesRead, contentLength)
        }

        Glide.with(this)
            .load(TEST_IMAGE_URL)
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    setUiState(UiState.Error("Error: ${e?.message ?: "Unknown error"}"))
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    setUiState(UiState.Done("Image loaded!"))
                    return false
                }
            })
            .into(binding.imagePreview)
    }

    private fun createProgressListener() = ProgressListener { bytesRead, contentLength, _ ->
        runOnUiThread {
            updateProgress(bytesRead, contentLength)
        }
    }

    private fun updateProgress(bytesRead: Long, contentLength: Long) {
        if (contentLength > 0) {
            val percent = ((100L * bytesRead) / contentLength).toInt()
            binding.progressBar.isIndeterminate = false
            binding.progressBar.progress = percent
            binding.progressText.text = getString(
                R.string.progress_format,
                percent,
                formatBytes(bytesRead),
                formatBytes(contentLength)
            )
        } else {
            binding.progressBar.isIndeterminate = true
            binding.progressText.text = getString(
                R.string.progress_unknown,
                formatBytes(bytesRead)
            )
        }
    }

    private fun setUiState(state: UiState) {
        when (state) {
            UiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.statusText.visibility = View.VISIBLE
            }
            is UiState.Done -> {
                binding.statusText.text = state.message
            }
            is UiState.Error -> {
                binding.statusText.text = state.message
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        return "%.1f MB".format(mb)
    }

    private sealed class UiState {
        data object Loading : UiState()
        data class Done(val message: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    companion object {
        private const val TEST_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/PNG_transparency_demonstration_1.png/600px-PNG_transparency_demonstration_1.png"
    }
}
