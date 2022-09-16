package com.distbit.nebuia_plugin.utils

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.*
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.exifinterface.media.ExifInterface
import com.otaliastudios.cameraview.frame.Frame
import com.otaliastudios.cameraview.size.Size
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.abs


class Utils {
    companion object {

        fun Frame.toBitMap(): Bitmap {
            val rotation: Int = this.rotationToUser
            val data = this.getData<ByteArray>()
            val yuvImage = YuvImage(
                data,
                this.format,
                this.size.width,
                this.size.height,
                null
            )

            val stream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(
                    0, 0,
                    this.size.width,
                    this.size.height
                ), 100, stream
            )

            val jpegByteArray = stream.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(
                jpegByteArray,
                0, jpegByteArray.size
            )

            return when (rotation) {
                90 -> rotateImage(bitmap, 90F)
                180 -> rotateImage(bitmap, 180F)
                270 -> rotateImage(bitmap, 270F)
                else -> bitmap
            }
        }

        fun Bitmap.correctOrientation(path: String): Bitmap {
            val ei = ExifInterface(path)
            val orientation: Int = ei.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_NORMAL -> return this
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    matrix.setRotate(180f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.setRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.setRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
                else -> return this
            }

            val bmRotated: Bitmap = Bitmap.createBitmap(
                this,
                0,
                0,
                this.width,
                this.height,
                matrix,
                true
            )
            this.recycle()

            return bmRotated

        }

        private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
            val matrix = Matrix()
            matrix.postRotate(angle)
            return Bitmap.createBitmap(
                source, 0, 0, source.width, source.height,
                matrix, true
            )
        }

        @Throws(JSONException::class)
        fun toMap(json: JSONObject): HashMap<String, Any> {
            val map: MutableMap<String, Any> = HashMap()
            val keys = json.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                var value = json[key]

                if (value is JSONArray) {
                    value = toList(value)
                } else if (value is JSONObject) {
                    value = toMap(value)
                }

                map[key] = value
            }

            return HashMap(map)
        }

        @Throws(JSONException::class)
        private fun toList(array: JSONArray): List<Any> {
            val list: MutableList<Any> = ArrayList()
            for (i in 0 until array.length()) {
                var value = array[i]
                if (value is JSONArray) {
                    value = toList(value)
                } else if (value is JSONObject) {
                    value = toMap(value)
                }
                list.add(value)
            }
            return list
        }

        @Throws(JSONException::class)
        fun getJsonFromMap(map: Map<String, Any>): JSONObject {
            val jsonData = JSONObject()
            for (key in map.keys) {
                var value = map[key]
                if (value is Map<*, *>) {
                    value = getJsonFromMap(value as Map<String, Any>)
                }
                jsonData.put(key, value)
            }
            return jsonData
        }

        fun Bitmap.toArray(): ByteArray {
            ByteArrayOutputStream().apply {
                compress(Bitmap.CompressFormat.JPEG,75,this)
                return toByteArray()
            }
        }

        fun ByteArray.toBitMap(): Bitmap =
            BitmapFactory.decodeByteArray(this, 0, this.size)


        fun Window.hideSystemUI() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                this.setDecorFitsSystemWindows(false)
                this.insetsController?.let {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                this.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
            }
        }

        /**
         * Play warning sound.
         */
        fun warning(context: Context) {
            try {
                val afd: AssetFileDescriptor = context.assets.openFd("warning.mp3")
                val mMediaPlayer = MediaPlayer()
                mMediaPlayer.setDataSource(
                    afd.fileDescriptor,
                    afd.startOffset,
                    afd.length
                )
                afd.close()
                mMediaPlayer.prepare()
                mMediaPlayer.start()
            } catch (ex: Exception) {

            }
        }

        /**
         * Calculate the optimal size of camera preview
         * @param sizes
         * @param w
         * @param h
         * @return
         */
        fun getOptimalSize(sizes: List<Size>?, w: Int, h: Int): Size? {
            val targetRatio = w.toDouble() / h
            if (sizes == null) return null
            var optimalSize: Size? = null
            var minDiff = Double.MAX_VALUE

            for (size in sizes) {
                val ratio: Int = size.width / size.height
                if (abs(ratio - targetRatio) > 0.2) continue
                if (abs(size.height - h) < minDiff) {
                    optimalSize = size
                    minDiff = abs(size.height - h).toDouble()
                }
            }
            // Cannot find the one match the aspect ratio, ignore the requirement
            if (optimalSize == null) {
                minDiff = Double.MAX_VALUE
                for (size in sizes) {
                    if (abs(size.height - h) < minDiff) {
                        optimalSize = size
                        minDiff = abs(size.height - h).toDouble()
                    }
                }
            }

            return optimalSize
        }
    }
}