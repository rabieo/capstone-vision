package com.example.capstone

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.capstone.utils.GraphicOverlay
import com.google.codelab.mlkit.FaceContourGraphic
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var viewFinder: PreviewView? = null

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var happinessView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        graphicOverlay = findViewById(R.id.graphic_overlay)
        happinessView = findViewById(R.id.happiness)

        if(allPermissionsGranted())
            startCamera()
        else
            ActivityCompat.requestPermissions(this,
                MainActivity.REQUIRED_PERMISSIONS,
                MainActivity.REQUEST_CODE_PERMISSIONS)

        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            preview = Preview.Builder().build()
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(480,360))
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { imageProxy ->
                        processImage(imageProxy)
                    })
                }

            val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

            try {
                cameraProvider.unbindAll()

                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

                preview?.setSurfaceProvider(viewFinder!!.surfaceProvider)

                val cameraControl = camera!!.getCameraControl()
                cameraControl.setZoomRatio(0.5f)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun processImage(imageProxy: ImageProxy) {
        val image = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
        val realTimeOpts = FaceDetectorOptions.Builder()
            .enableTracking()
            .build()
        val scanner = FaceDetection.getClient(realTimeOpts)
        scanner.process(image)
            .addOnSuccessListener { faces ->
                processFaces(faces)
            }
            .addOnFailureListener{ e -> // Task failed with an exception
                e.printStackTrace()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    //.setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)

    private fun processFaces(faces: List<Face>) {
        for (face in faces) {

            // If classification was enabled:
            if (face.smilingProbability != null) {
                val smileProb = face.smilingProbability
                Log.d(TAG, "Smiling: " + smileProb!!.toString())
                val mood = (smileProb * 100).roundToInt()
                happinessView.text = if (mood > 75) "Happy" else if (mood > 25) "O.K" else "Meh"
            }

            graphicOverlay.clear()
            val graphic = FaceContourGraphic(graphicOverlay)
            graphicOverlay.add(graphic)
            graphic.updateFace(face)
        }
    }

    /*
    private fun handleResult() {
        val intent = Intent()
        intent.putExtra("scanned_text", "Done with facial recognition")
        setResult(Activity.RESULT_OK, intent)
        finish()
    }*/

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    fun onCancelTapped(view: View) {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val TAG = "FaceActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private val TAGG = "TWESBTSCANNER"
        val BLUETOOTH_REQUESTCODE = 1
    }

}