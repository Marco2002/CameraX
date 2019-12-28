// TODO change gif files storage
// TODO delete temp stored images on app close


package com.nano.camerax

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.WorkerThread
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

    fun export(exportFile: File) {
        // generate gif
        val bitmaps = ArrayList<Bitmap>()
        images.forEach {
            bitmaps.add(BitmapFactory.decodeFile(it))
            File(it).delete()
        }

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

        // reset image list
        images.clear()

        // export gif
        val outStream: FileOutputStream?
        try {
            outStream = FileOutputStream(exportFile)
            outStream.write(gif)
            outStream.close()
        } catch (e: java.lang.Exception) {
            Log.d("MuraMasaHandler", e.message)
        }
    }

}