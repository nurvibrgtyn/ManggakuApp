package com.nurvi.mangoleavesdetect

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nurvi.mangoleavesdetect.databinding.ActivityMainBinding
import com.nurvi.mangoleavesdetect.ml.ConvertedModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.FileUtil.loadLabels
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var labels: List<String>
    private lateinit var binding: ActivityMainBinding
    private lateinit var imageView: ImageView
    private lateinit var btnLoadImage: Button
    private lateinit var btnCaptureImage: Button
    private lateinit var tvOutput: TextView
    private lateinit var tvDescription: TextView
    private lateinit var descriptions: List<List<String>>
    private val GALLERY_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageView = binding.imageView
        btnLoadImage = binding.btnLoadImage
        btnCaptureImage = binding.btnCaptureImage
        tvOutput = binding.tvOutput
        tvDescription = binding.tvDescription

        // Load labels from assets
        labels = loadLabels("labels.txt")
        descriptions = loadDescriptions("descriptions.txt")

        btnCaptureImage.setOnClickListener {
            Log.d("MainActivity", "Capture Image button clicked")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                takePicturePreview.launch(null)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        btnLoadImage.setOnClickListener {
            Log.d("MainActivity", "Load Image button clicked")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                onGalleryResult.launch(intent)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        tvOutput.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${tvOutput.text}"))
            startActivity(intent)
        }

        imageView.setOnLongClickListener {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            true
        }
    }

    // Function to load labels from assets folder
    fun loadLabels(filename: String): List<String> {
        return try {
            val reader = BufferedReader(InputStreamReader(assets.open(filename)))
            reader.readLines()
        } catch (e: IOException) {
            Log.e("MainActivity", "Error reading labels file", e)
            emptyList()
        }
    }

    // Function to load descriptions from assets folder
    fun loadDescriptions(filename: String): List<List<String>> {
        val descriptionsList = mutableListOf<List<String>>()
        var currentDescription = mutableListOf<String>()

        try {
            val reader = BufferedReader(InputStreamReader(assets.open(filename)))
            var line = reader.readLine()

            while (line != null) {
                if (line.isNotBlank()) {
                    currentDescription.add(line)
                } else {
                    if (currentDescription.isNotEmpty()) {
                        descriptionsList.add(currentDescription.toList())
                        currentDescription.clear()
                    }
                }
                line = reader.readLine()
            }

            // Add last description if not empty
            if (currentDescription.isNotEmpty()) {
                descriptionsList.add(currentDescription.toList())
            }

            reader.close()
        } catch (e: IOException) {
            Log.e("MainActivity", "Error reading descriptions file", e)
        }

        return descriptionsList
    }

    // Request permission launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission granted, proceed with the action
            } else {
                Toast.makeText(this, "Permission Denied! Try Again", Toast.LENGTH_SHORT).show()
            }
        }

    // Launch camera and take picture
    private val takePicturePreview =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let {
                imageView.setImageBitmap(it)
                outputGenerator(it)
            } ?: run {
                Log.e("MainActivity", "Bitmap is null")
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        }

    // Get image from gallery
    private val onGalleryResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    try {
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        imageView.setImageBitmap(bitmap)
                        outputGenerator(bitmap)
                    } catch (e: IOException) {
                        Log.e("MainActivity", "Error decoding bitmap", e)
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                } ?: run {
                    Log.e("MainActivity", "Uri is null")
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("MainActivity", "Result code not OK")
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }


    private fun outputGenerator(bitmap: Bitmap) {
        val model = ConvertedModel.newInstance(this)

        // Resize the bitmap to 128x128 pixels
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 128, 128, true)

        // Convert the bitmap to a TensorImage
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(resizedBitmap)

        // Create the input TensorBuffer
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 128, 128, 3), DataType.FLOAT32)
        inputFeature0.loadBuffer(tensorImage.buffer)

        // Run the model inference
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

        // Get the result with the highest probability
        val maxProbabilityIndex = getMaxProbabilityIndex(outputFeature0.floatArray)
        val resultText = "${labels[maxProbabilityIndex]}"
        tvOutput.text = resultText
        //val resultProbability = "Probability: ${outputFeature0.floatArray[maxProbabilityIndex]}"
        //tvProb.text = resultProbability

        // Set description text based on classification result
        val description = getDescriptionForResult(maxProbabilityIndex)
        tvDescription.text = description.joinToString("\n\n") // Join paragraphs with two newlines

        // Close the model to release resources
        model.close()
    }

    // Function to get description based on classification result
    private fun getDescriptionForResult(index: Int): List<String> {
        return if (index >= 0 && index < descriptions.size) {
            descriptions[index]
        } else {
            emptyList()
        }
    }

    // Function to download image to device
    /*private val requestPermissionLauncherDownload =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                AlertDialog.Builder(this).setTitle("Download Image?")
                    .setMessage("Do you want to download this image to your device?")
                    .setPositiveButton("Yes") { _, _ ->
                        val drawable: BitmapDrawable = imageView.drawable as BitmapDrawable
                        val bitmap = drawable.bitmap
                        downloadImage(bitmap)
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } else {
                Toast.makeText(this, "Please allow permission to download", Toast.LENGTH_LONG).show()
            }
        }

    private fun downloadImage(mBitmap: Bitmap): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Leaves_Images" + System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return try {
            if (uri != null) {
                val imageUri = contentResolver.insert(uri, contentValues)
                imageUri?.let {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        if (!mBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                            throw IOException("Couldn't save the bitmap")
                        } else {
                            Toast.makeText(applicationContext, "Image Saved", Toast.LENGTH_LONG).show()
                            return imageUri
                        }
                    }
                }
                null
            } else {
                null
            }
        } catch (e: IOException) {
            Log.e("MainActivity", "Error saving image", e)
            Toast.makeText(applicationContext, "Failed to save image", Toast.LENGTH_LONG).show()
            null
        }
    }*/

    fun getMaxProbabilityIndex(arr: FloatArray): Int {
        var maxVal = 0.0f
        var maxValIndex = -1
        for (i in arr.indices) {
            if (arr[i] > maxVal) {
                maxVal = arr[i]
                maxValIndex = i
            }
        }
        return maxValIndex
    }
}
