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

    // État UI
    val imageBitmap = mutableStateOf<Bitmap?>(null)
    val predictionResult = mutableStateOf<String?>(null)

    // Constantes
    private val imageSizeX = 224
    private val imageSizeY = 224
    private val minCookingTime = 51f
    private val maxCookingTime = 410f

    // Classes de haricots
    private val beanClasses = listOf(
        "Dor701", "Escapan021", "GPL190C", "GPL190S",
        "Macc55", "NIT4G16187", "Senegalais", "TY339612", "autre"
    )

    // Chargement modèle
    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Lancer pipeline : classification -> régression
     */
    fun runPrediction(context: Context, bitmap: Bitmap) {
        imageBitmap.value = bitmap
        predictionResult.value = "Analyse en cours..."

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val argbBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, true)
                } else bitmap

                // Étape 1 : Classification
                val (predictedClass, _) = runClassification(context, argbBitmap)

                withContext(Dispatchers.Main) {
                    if (predictedClass == "autre") {
                        predictionResult.value =
                            "L’image fournie n’est pas reconnue comme un haricot.\n" +
                                    "L’application ne prédit que le temps de cuisson des haricots."
                    } else {
                        // Étape 2 : Régression
                        var result: Float = 0f
                        val latence = measureTime {
                            result = runRegression(context, argbBitmap)
                        }
                        predictionResult.value =
                            "Variété détectée : $predictedClass\n" +
                                    "Temps de cuisson estimé : ${result.toInt()} minutes\n" +
                                    "(Latence : ${latence.inWholeMilliseconds} ms)"
                    }
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
     * Classification int8
     */
    /**
     * Classification int8 (entrée UINT8, sortie UINT8)
     */
    private fun runClassification(context: Context, bitmap: Bitmap): Pair<String, Int> {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, imageSizeX, imageSizeY, true)

        // Entrée UINT8
        val inputBuffer = ByteBuffer.allocateDirect(imageSizeX * imageSizeY * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until imageSizeY) {
            for (x in 0 until imageSizeX) {
                val pixel = resizedBitmap.getPixel(x, y)
                val r = (pixel shr 16 and 0xFF).toByte()
                val g = (pixel shr 8 and 0xFF).toByte()
                val b = (pixel and 0xFF).toByte()
                inputBuffer.put(r)
                inputBuffer.put(g)
                inputBuffer.put(b)
            }
        }
        inputBuffer.rewind()

        // Modèle quantifié int8
        val interpreter = Interpreter(loadModelFile(context, "bean_classifier.tflite"))

        // Sortie UINT8 (quantifiée)
        val outputBuffer = ByteBuffer.allocateDirect(beanClasses.size)
        outputBuffer.order(ByteOrder.nativeOrder())
        interpreter.run(inputBuffer, outputBuffer)
        interpreter.close()

        // Convertir la sortie en int [0–255]
        outputBuffer.rewind()
        val scores = IntArray(beanClasses.size)
        for (i in scores.indices) {
            scores[i] = outputBuffer.get().toInt() and 0xFF
        }

        // Trouver l’index avec le score max
        val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: -1
        return Pair(beanClasses[maxIdx], maxIdx)
    }


    /**
     * Régression float32
     */
    private fun runRegression(context: Context, bitmap: Bitmap): Float {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, imageSizeX, imageSizeY, true)

        // Entrée float32 [0.0 - 1.0]
        val inputImage = Array(1) { Array(imageSizeY) { Array(imageSizeX) { FloatArray(3) } } }

        for (y in 0 until imageSizeY) {
            for (x in 0 until imageSizeX) {
                val pixel = resizedBitmap.getPixel(x, y)
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                inputImage[0][y][x][0] = r
                inputImage[0][y][x][1] = g
                inputImage[0][y][x][2] = b
            }
        }

        // Chargement du modèle float16
        val interpreter = Interpreter(loadModelFile(context, "bean_regressor.tflite"))

        // Sortie float32 (même si le modèle est float16, TFLite reconvertit en float32)
        val output = Array(1) { FloatArray(1) }
        interpreter.run(inputImage, output)
        interpreter.close()

        return denormalize(output[0][0])
    }
    // Dénormalisation
    private fun denormalize(normalizedValue: Float): Float {
        return normalizedValue * (maxCookingTime - minCookingTime) + minCookingTime
    }

    // Réinitialiser
    fun reset() {
        imageBitmap.value = null
        predictionResult.value = null
    }
}
