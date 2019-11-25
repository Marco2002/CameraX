package com.nano.camerax

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MuraMasaHandler {
    private var imageCollection: ArrayList<String> = ArrayList()
    private val rotationMatrix = Matrix().apply { postRotate(90f) }

    val lastImage: String
        get() {
            return imageCollection.last()
        }

    fun addImage(image: String) {
        imageCollection.add(image)
    }

    @WorkerThread
    suspend fun loadImage(imagePath: String): Bitmap {
        return withContext(Dispatchers.Default) {
            BitmapFactory.decodeFile(imagePath)
        }
    }

}