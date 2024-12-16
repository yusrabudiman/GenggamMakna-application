package com.example.genggammakna.ui.page

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.genggammakna.R
import com.example.genggammakna.reftrofit.ApiConfig
import com.example.genggammakna.response.PredictionResponse
import retrofit2.Response
import kotlinx.coroutines.launch
import android.net.Uri
import android.provider.MediaStore
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

@Suppress("DEPRECATION")
class UploadActivity : AppCompatActivity() {

    private lateinit var imageButton: ImageButton
    private lateinit var imageView: ImageView
    private lateinit var resultText: TextView
    private lateinit var detectButton: Button
    private lateinit var progressBar: ProgressBar
    private var selectedImageUri: Uri? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                selectedImageUri = uri
                imageView.setImageURI(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)
        supportActionBar?.hide()
        enableEdgeToEdge()
        setupViews()
        setupListeners()
        onBackPressedDispatcher()
    }

    private fun setupViews() {
        imageButton = findViewById(R.id.imageButtonSibindo)
        imageView = findViewById(R.id.imageViewDisplay)
        resultText = findViewById(R.id.resultText)
        detectButton = findViewById(R.id.detectButton)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        imageButton.setOnClickListener {
            pickImage.launch("image/*")
        }

        detectButton.setOnClickListener {
            if (selectedImageUri != null) {
                detectImage(selectedImageUri!!)
            } else {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun detectImage(imageUri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            val file = bitmapToFile(bitmap)
            val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)

            progressBar.visibility = ProgressBar.VISIBLE

            lifecycleScope.launch {
                try {
                    val response: Response<PredictionResponse> = ApiConfig.getApiService().predictImage(part)
                    progressBar.visibility = ProgressBar.GONE
                    if (response.isSuccessful) {
                        val predictionResponse = response.body()
                        if (predictionResponse != null) {
                            resultText.text = "Result: ${predictionResponse.predicted_alphabet}"
                        } else {
                            resultText.text = "Error: No response body received"
                        }
                    } else {
                        resultText.text = "Error: ${response.message()}"
                    }
                } catch (e: Exception) {
                    progressBar.visibility = ProgressBar.GONE
                    resultText.text = "Failed to connect: ${e.message}"
                }
            }
        } catch (e: Exception) {
            progressBar.visibility = ProgressBar.GONE
            resultText.text = "Failed to process image: ${e.message}"
        }
    }

    private fun bitmapToFile(bitmap: Bitmap): File {
        val file = File(cacheDir, "temp_image.jpg")
        val fileOutputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 20, fileOutputStream)
        fileOutputStream.flush()
        fileOutputStream.close()
        return file
    }

    private fun onBackPressedDispatcher(){
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (progressBar.visibility == ProgressBar.VISIBLE) {
                    Toast.makeText(this@UploadActivity,
                        getString(R.string.please_wait), Toast.LENGTH_SHORT).show()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
