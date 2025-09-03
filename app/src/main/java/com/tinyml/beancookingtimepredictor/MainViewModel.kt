package com.tinyml.beancookingtimepredictor

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.time.measureTime

class MainViewModel : ViewModel() {

    // État de l'interface utilisateur
    val imageBitmap = mutableStateOf<Bitmap?>(null)
    val predictionResult = mutableStateOf<String?>(null)

    // Constantes pour le modèle
    private val imageSizeX = 224
    private val imageSizeY = 224
    private val minCookingTime = 51f
    private val maxCookingTime = 410f

    // Charge le modèle TensorFlow Lite depuis les assets
    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Lance la prédiction dans une coroutine pour ne pas bloquer l'UI
     */
    fun runPrediction(context: Context, bitmap: Bitmap) {
        imageBitmap.value = bitmap
        predictionResult.value = "Calcul en cours..."

        viewModelScope.launch(Dispatchers.Default) {
            try {
                var result:Float = 0f
                val latence = measureTime {
                    result = runModelInference(context, bitmap)
                }

                print("La latence pour ce modèle est de ${latence.inWholeMilliseconds} ms")
                withContext(Dispatchers.Main) {
                    predictionResult.value =
                        "Temps de cuisson estimé : ${result.toInt()} minutes\nLe temps d'inférence pour ce modèle est de ${latence.inWholeMilliseconds} ms"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    predictionResult.value = "Erreur lors de la prédiction."
                }
                Log.e("TFLITE", "Erreur : ${e.message}", e)
            }
        }
    }

    /**
     * Inférence du modèle TFLite float32
     */
    private fun runModelInference(context: Context, bitmap: Bitmap): Float {
        // 1. Assurer ARGB_8888
        val argbBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            bitmap
        }

        // 2. Redimensionner à 224x224
        val resizedBitmap = Bitmap.createScaledBitmap(argbBitmap, imageSizeX, imageSizeY, true)

        // 3. Créer le ByteBuffer pour le modèle float32
        val inputBuffer = ByteBuffer.allocateDirect(4 * imageSizeX * imageSizeY * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        // 4. Remplir le buffer avec les pixels normalisés [0,1]
        for (y in 0 until imageSizeY) {
            for (x in 0 until imageSizeX) {
                val pixel = resizedBitmap.getPixel(x, y)
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }
        }
        inputBuffer.rewind()

        // 5. Charger l'interpréteur
        val interpreter = Interpreter(loadModelFile(context, "model.tflite"))

        // 6. Préparer le buffer de sortie
        val outputBuffer = ByteBuffer.allocateDirect(4) // 1 float32
        outputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.rewind()

        // 7. Exécuter l'inférence
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        val prediction = outputBuffer.float

        interpreter.close()

        // 8. Dénormaliser la prédiction
        return denormalize(prediction)
    }

    // Dénormalise la prédiction de [0,1] vers le temps réel
    private fun denormalize(normalizedValue: Float): Float {
        return normalizedValue * (maxCookingTime - minCookingTime) + minCookingTime
    }

    // Réinitialise l'état
    fun reset() {
        imageBitmap.value = null
        predictionResult.value = null
    }
}
