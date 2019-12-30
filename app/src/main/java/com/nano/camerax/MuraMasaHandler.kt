package com.nano.camerax

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


class MuraMasaHandler {
    private var images: ArrayList<String> = ArrayList()

    val lastImage: String
        get() {
            return images.last()
        }

    val nextFilename: String
        get() {
            return images.size.toString() + ".jpg"
        }

    fun addImage(image: String) {
        images.add(image)
    }

    @WorkerThread
    suspend fun loadImage(imagePath: String): Bitmap {
        return withContext(Dispatchers.Default) {
            BitmapFactory.decodeFile(imagePath)
        }
    }

    fun reset() {
        // delete each file
        images.forEach {
            File(it).delete()
        }
        // reset image list
        images.clear()
    }


//    fun exportMp4(exportFile: File): String {
//        try {
//            val encoder = SequenceMuxerJpg(exportFile)
//            val iterator: MutableIterator<String> = images.iterator()
//            while (iterator.hasNext()) {
//                val image = File(iterator.next())
//                if (!image.exists() || image.length().toInt() == 0) {
//                    continue
//                }
//                try {
//                    encoder.encodeImage(image)
//                    Log.d("Video Encoding", "Image encoded")
//                } catch (e: Exception) {
//                    Log.d("Video Encoding", e.stackTrace.toString())
//                }
//            }
//            encoder.finish()
//        } catch (e: IOException) {
//            Log.d("Video Encoding", e.stackTrace.toString())
//        }
//        return exportFile.absolutePath
//    }

    suspend fun export(exportFile: File, context: Context) = withContext(Dispatchers.IO) {
        // TODO show animation while exporting

        // create Folder if it does not exist
        val folder = exportFile.parentFile
        var success = true
        if (!folder.exists()) {
            success = folder.mkdirs()
        }
        if (success) {

            // generate gif
            val bitmaps = ArrayList<Bitmap>()
            images.forEach {
                bitmaps.add(BitmapFactory.decodeFile(it))
            }
            reset()

            // init encoder
            val bos = ByteArrayOutputStream()
            val encoder = AnimatedGifEncoder()
            encoder.setFrameRate(7f)
            encoder.start(bos)
            // first loop encodes each image
            bitmaps.forEach { encoder.addFrame(it) }
            // second loop encodes the images backwards
            for (i in (bitmaps.size - 2) downTo 1) encoder.addFrame(bitmaps[i])
            encoder.finish()
            val gif = bos.toByteArray()

            // export gif
            val outStream: FileOutputStream?
            try {
                outStream = FileOutputStream(exportFile)
                outStream.write(gif)
                outStream.close()
                // save File to storage
                Intent(MediaStore.ACTION_VIDEO_CAPTURE).also { takeVideoIntent ->

                    // Continue only if the File was successfully created
                    exportFile.also {
                        val fileURI: Uri = FileProvider.getUriForFile(
                            context,
                            "com.nano.camerax",
                            it
                        )
                        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileURI)
                        // startActivityForResult(MainActivity(), takePictureIntent, 1, null)
                    }

                }

                Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
                    val f = File(exportFile.absolutePath)
                    mediaScanIntent.data = Uri.fromFile(f)
                    context.sendBroadcast(mediaScanIntent)
                }

            } catch (e: java.lang.Exception) {
                Log.d("MuraMasaHandler", e.message)
            }

        }

    }

}