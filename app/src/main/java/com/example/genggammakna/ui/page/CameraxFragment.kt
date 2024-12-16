package com.example.genggammakna.ui.page

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.genggammakna.R
import com.example.genggammakna.databinding.FragmentCameraxBinding
import com.example.genggammakna.ml.Modelslfinal
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraxFragment : Fragment() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var binding: FragmentCameraxBinding
    private lateinit var model: Modelslfinal
    private var detectedSentence = StringBuilder()
    private val alphabet = ('A'..'Z').toList()
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera(binding.previewView)
        } else {
            handleCameraPermissionDenied()
        }
    }

    private var isProcessing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCameraxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        updateStatusMessage(getString(R.string.initializing_model))
        Executors.newSingleThreadExecutor().execute {
            model = Modelslfinal.newInstance(requireContext())
            activity?.runOnUiThread {
                updateStatusMessage(getString(R.string.model_ready_done))
                checkAndRequestCameraPermission()
            }
        }
    }

    private fun checkAndRequestCameraPermission() {
        when {
            isCameraPermissionGranted() -> startCamera(binding.previewView)
            else -> requestCameraPermission()
        }
    }

    private fun isCameraPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun handleCameraPermissionDenied() {
        updateStatusMessage(getString(R.string.camera_permission_denied))
    }

    private fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (::model.isInitialized) {
                            processImageProxy(imageProxy)
                        }
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        if (!::model.isInitialized || isProcessing) {
            imageProxy.close()
            return
        }

        isProcessing = true
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)
            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
            inputFeature0.loadBuffer(byteBuffer)
            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer
            val result = extractResult(outputFeature0)

            activity?.runOnUiThread {
                detectedSentence.append(result)
                binding.tvDetectedSentence.text = detectedSentence.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
            isProcessing = false
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        val yuvImage = YuvImage(bytes, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val byteArray = out.toByteArray()
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3)
        buffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(224 * 224)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixelValue in intValues) {
            buffer.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f)
            buffer.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)
            buffer.putFloat((pixelValue and 0xFF) / 255.0f)
        }

        return buffer
    }

    private fun extractResult(outputBuffer: TensorBuffer): String {
        val confidences = outputBuffer.floatArray
        val maxIdx = confidences.indices.maxByOrNull { confidences[it] } ?: -1
        return if (maxIdx in alphabet.indices) {
            alphabet[maxIdx].toString()
        } else {
            ""
        }
    }

    private fun updateStatusMessage(message: String) {
        activity?.runOnUiThread {
            binding.tvDetectedSentence.text = message
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::model.isInitialized && !isProcessing) {
            model.close()
        }
        cameraExecutor.shutdown()
    }
}
