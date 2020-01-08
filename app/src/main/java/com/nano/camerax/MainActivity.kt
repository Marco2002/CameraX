package com.nano.camerax

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

// TODO show angle Rotations
// TODO bug which disables the viewFinder when reopening app
// TODO gif preview
// TODO set app name

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
    private lateinit var saveButton: ImageButton
    private lateinit var cancelButton: ImageButton
    private lateinit var imageCapture: ImageCapture
    private val muraMasaHandler = MuraMasaHandler()
    private val captureAnimation = AlphaAnimation(0f, 1f)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // init viewFinder and set responsive width and height
        viewFinder = findViewById(R.id.view_finder)
        viewFinder.post {
            viewFinder.layoutParams.height = (viewFinder.width * (4.0 / 3.0)).toInt()

        }

        // init previousPreview
        lastImagePreview = findViewById(R.id.last_image_preview)
        // previousPreview by default loads the image rotated by 90 degrees
        // to compensate this the view needs to bee rotated by 90 degrees (see xml)
        // and the width, height and rotation pivot needs to be set as followed
        lastImagePreview.post {
            lastImagePreview.layoutParams.apply {
                height = viewFinder.width
                width = (viewFinder.width.toFloat() * (4f / 3f)).toInt()
            }
            lastImagePreview.pivotX = viewFinder.width / 2f
            lastImagePreview.pivotY = viewFinder.width / 2f
        }

        // capture animation init
        // animate alpha to quickly go from 0 to 1 and back to 0
        // to simulate photo camera capture effect
        captureAnimation.apply {
            duration = 80
            repeatMode = Animation.REVERSE
            repeatCount = 1
        }

        // flash button
        findViewById<ToggleButton>(R.id.flash_button).setOnCheckedChangeListener { _, isChecked ->
            imageCapture.flashMode = if (isChecked) FlashMode.ON else FlashMode.OFF
        }

        // save button:
        saveButton = findViewById(R.id.save_button)
        saveButton.visibility = View.INVISIBLE
        saveButton.setOnClickListener {
            lastImagePreview.setImageResource(android.R.color.transparent)
            saveButton.visibility = View.INVISIBLE
            cancelButton.visibility = View.INVISIBLE

            val context = this as Context
            MainScope().launch {
                muraMasaHandler.export(
                    File(
                        getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
                                + File.separator + "MuraMasa",
                        "MuraMasa${System.currentTimeMillis()}.gif"
                    ), context
                )
            }
        }

        // cancel button:
        cancelButton = findViewById(R.id.cancel_button)
        cancelButton.visibility = View.INVISIBLE
        cancelButton.setOnClickListener {
            lastImagePreview.setImageResource(android.R.color.transparent)
            saveButton.visibility = View.INVISIBLE
            cancelButton.visibility = View.INVISIBLE
            muraMasaHandler.reset()
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

                // flash mode
                if (findViewById<ToggleButton>(R.id.flash_button).isChecked) {
                    setFlashMode(FlashMode.ON)
                } else {
                    setFlashMode(FlashMode.OFF)
                }
            }.build()

        // Build the image capture use case and attach button click listener
        imageCapture = ImageCapture(imageCaptureConfig)
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
                            captureEffect()
                            if (cancelButton.visibility == View.INVISIBLE) {
                                // enable cancel button after first image is taken
                                cancelButton.visibility = View.VISIBLE
                            } else if (saveButton.visibility == View.INVISIBLE) {
                                // enable save button after second image is taken
                                saveButton.visibility = View.VISIBLE
                            }

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

    private fun captureEffect() {
        // make lastImagePreview all black but with 0 alpha
        lastImagePreview.alpha = 1f
        lastImagePreview.setImageDrawable(getDrawable(R.color.colorPrimary))

        lastImagePreview.animation = captureAnimation
        lastImagePreview.animation.start()

        // set alpha to 0.5 10ms before animation ends
        // to prevent an all black frame in between
        Handler().postDelayed({
            lastImagePreview.alpha = 0.5f
            MainScope().launch {
                // update lastImagePreview
                lastImagePreview.setImageBitmap(
                    muraMasaHandler.loadImage(
                        muraMasaHandler.lastImage
                    )
                )
            }
        }, 70)
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
