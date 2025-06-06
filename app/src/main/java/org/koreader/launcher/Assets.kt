package org.koreader.launcher

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import org.koreader.launcher.extensions.pruneCacheDir
import java.io.*

class Assets {

    private val tag = this::class.java.simpleName

    init {
        Log.i(tag, "loading ${BuildConfig.SEVENZIP_LIB}")
        System.loadLibrary(BuildConfig.SEVENZIP_LIB)
    }

    fun extract(activity: Activity): Boolean {
        return if (isNewBundle(activity)) {
            val startTime = System.nanoTime()
            val result = bootstrap(activity)
            val elapsedTime = System.nanoTime() - startTime
            Log.i(tag, "update installed in ${elapsedTime / 1000000} milliseconds")
            activity.pruneCacheDir()
            result
        } else {
            true
        }
    }

    private fun isNewBundle(context: Context): Boolean {
        val path = "${context.filesDir.absolutePath}/git-rev"
        return try {
            if (!File(path).exists()) {
                Log.i(tag, "New install")
                return true
            }
            context.assets.open("module/version.txt").bufferedReader().use {
                val version = it.readLine()
                val fileReader = FileReader(File(path).absolutePath)
                val bufferedReader = BufferedReader(fileReader)
                val installedVersion = bufferedReader.readLine()
                bufferedReader.close()
                return if (version == installedVersion) {
                    Log.i(tag, "Skip installation for revision $version")
                    false
                } else {
                    Log.i(tag, "Found new package revision $version")
                    true
                }
            }
        } catch (e: Exception) {
            Log.i(tag, "New install")
            true
        }
    }

    private fun bootstrap(activity: Activity): Boolean {
        val filesDir = activity.filesDir.absolutePath
        activity.runOnUiThread { dialog = FramelessProgressDialog.show(activity, "") }

        // remove old files
        try {
            File(filesDir).deleteRecursively()
            File(filesDir).mkdir()
        } catch (e: IOException) {
            Log.e(tag, "Error prunning old files: $e")
        }

        /* copy regular files and extract 7z files from assets store */
        activity.assets.list("module")?.let { bundle ->
            for (asset in bundle) {
                val assetName = "module/$asset"
                if (assetName != "module/version.txt") {
                    when {
                        (asset.endsWith("7z")) -> {
                            /* Extract all 7z files in assets store */
                            Log.v(tag, "Uncompressing $assetName")
                            try {
                                val ok = (extract(activity.assets, assetName, filesDir) == 0)
                                if (!ok)
                                    return false
                            } catch (e: Exception) {
                                Log.e(tag, "Error extracting 7z file: $e")
                                return false
                            }
                        }
                        else -> {
                            /* Copy all regular files in assets store */
                            Log.v(tag, "Extracting $assetName")
                            try {
                                val file = File(filesDir, asset)
                                val inputStream = activity.assets.open(assetName)
                                val outputStream = FileOutputStream(file)
                                inputStream.use { source ->
                                    outputStream.use { target ->
                                        source.copyTo(target)
                                    }
                                }
                                inputStream.close()
                                outputStream.flush()
                                outputStream.close()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }

        activity.runOnUiThread { dialog?.dismiss() }
        return true
    }

    /* dialog used while extracting assets from APK */
    private var dialog: FramelessProgressDialog? = null
    private class FramelessProgressDialog private constructor(context: Context):
        Dialog(context, R.style.FramelessDialog) {
        companion object {
            fun show(context: Context, title: CharSequence): FramelessProgressDialog {
                val dialog = FramelessProgressDialog(context)
                dialog.setTitle(title)
                dialog.setCancelable(false)
                dialog.setOnCancelListener(null)
                dialog.window?.setGravity(Gravity.BOTTOM)
                val progressBar = ProgressBar(context)
                try {
                    ContextCompat.getDrawable(context, R.drawable.discrete_spinner)
                        ?.let { spinDrawable -> progressBar.indeterminateDrawable = spinDrawable }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                /* The next line will add the ProgressBar to the dialog. */
                dialog.addContentView(progressBar, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
                )
                dialog.show()
                return dialog
            }
        }
    }

    private external fun extract(assetManager: AssetManager, payload: String, output: String): Int
}
