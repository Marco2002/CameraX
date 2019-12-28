package com.nano.camerax

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

// TODO show angle Rotations
// TODO improve save button
// TODO reset button
// TODO bug which disables the viewFinder when reopening app

// This is an arbitrary number we are using to keep track of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts.
private const val REQUEST_CODE_PERMISSIONS = 10

// This is an array of all the permission specified in the manifest.
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var viewFinder: TextureView
    private lateinit var lastImagePreview: ImageView
    private val muraMasaHandler = MuraMasaHandler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // init viewFinder and set responsive width and height
        viewFinder = findViewById(R.id.view_finder)
        viewFinder.post {
            viewFinder.layoutParams = ConstraintLayout.LayoutParams(
                viewFinder.width,
                (viewFinder.width * (4.0 / 3.0)).toInt()
            )
        }

        // init previousPreview
        lastImagePreview = findViewById(R.id.last_image_preview)
        // previousPreview by default loads the image rotated by 90 degrees
        // to compensate this the view needs to bee rotated by 90 degrees (see xml)
        // and the width, height and rotation pivot needs to be set as followed
        lastImagePreview.post {
            lastImagePreview.layoutParams = ConstraintLayout.LayoutParams(
                (viewFinder.width.toFloat() * (4f / 3f)).toInt(),
                viewFinder.width
            )
            lastImagePreview.pivotX = viewFinder.width / 2f
            lastImagePreview.pivotY = viewFinder.width / 2f
        }

        // save button
        findViewById<Button>(R.id.save_button).setOnClickListener {
            lastImagePreview.setImageResource(android.R.color.transparent)
            muraMasaHandler.export(
                File(
                    externalMediaDirs.first(),
                    "${System.currentTimeMillis()}.gif"
                )
            )
        }

        // Request camera permissions
        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }


        // Every time the provided texture view changes, recompute layout
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }
    }

    private fun startCamera() {
        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetResolution(Size(680, 480)) // 4/3 aspect Ratio
        }.build()


        // Build the viewfinder use case
        val preview = Preview(previewConfig)

        // Every time the viewfinder is updated, recompute layout
        preview.setOnPreviewOutputUpdateListener {

            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)

            viewFinder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        // Create configuration object for the image capture use case
        val imageCaptureConfig = ImageCaptureConfig.Builder()
            .apply {

                // We don't set a resolution for image capture; instead, we
                // select a capture mode which will infer the appropriate
                // resolution based on aspect ration and requested mode
                setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                setTargetResolution(Size(1080, 1440))
            }.build()

        // Build the image capture use case and attach button click listener
        val imageCapture = ImageCapture(imageCaptureConfig)
        findViewById<Button>(R.id.capture_button).setOnClickListener {
            val file = File(externalMediaDirs.first(), muraMasaHandler.nextFilename)

            imageCapture.takePicture(file, executor,
                object : ImageCapture.OnImageSavedListener {
                    override fun onError(
                        imageCaptureError: ImageCapture.ImageCaptureError,
                        message: String,
                        exc: Throwable?
                    ) {
                        val msg = "Photo capture failed: $message"
                        Log.e("CameraXApp", msg, exc)
                        viewFinder.post {
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onImageSaved(file: File) {
                        val msg = "Photo capture succeeded: ${file.absolutePath}"
                        Log.d("CameraXApp", msg)
                        viewFinder.post {
                            // Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            muraMasaHandler.addImage(file.absolutePath)
                            updateLastImagePreview()
                        }
                    }
                })
        }

        // Bind use cases to lifecycle
        CameraX.bindToLifecycle(this as LifecycleOwner, preview, imageCapture)
        // CameraX.bindToLifecycle(this as LifecycleOwner, preview, imageCapture, analyzerUseCase)
    }

    private fun updateTransform() {

        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when (viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix)
    }

    private fun updateLastImagePreview() = MainScope().launch {
        lastImagePreview.setImageBitmap(muraMasaHandler.loadImage(muraMasaHandler.lastImage))
    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.post { startCamera() }
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }
}
