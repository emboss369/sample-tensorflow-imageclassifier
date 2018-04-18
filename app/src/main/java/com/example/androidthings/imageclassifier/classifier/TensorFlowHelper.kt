/*
 * Copyright 2017 The Android Things Samples Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.androidthings.imageclassifier.classifier

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.experimental.and



/**
 * TensorFlow画像分類子のヘルパー関数。
 */
object TensorFlowHelper {

    private const val RESULTS_TO_SHOW = 3

    /**
     * Assetsでモデルファイルをメモリマップします。
     */
    @Throws(IOException::class)
    fun loadModelFile(context: Context, modelFile: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun readLabels(context: Context, labelsFile: String): List<String> {
        val assetManager = context.assets
        val result = ArrayList<String>()
        try {
            assetManager.open(labelsFile).use { iinputStream ->
                BufferedReader(InputStreamReader(iinputStream)).use { br ->
                    while (true) {
                        var line = br.readLine()
                        if (line == null) break
                        result.add(line)
                    }
                    return result
                }
            }
        } catch (ex: IOException) {
            throw IllegalStateException("Cannot read labels from $labelsFile")
        }

    }

    /**
     * 最高の分類を見つける。
     */
    fun getBestResults(labelProbArray: Array<ByteArray>,
                       labelList: List<String>): Collection<Recognition> {
        val sortedLabels = PriorityQueue(RESULTS_TO_SHOW,
                Comparator<Recognition> { lhs, rhs -> java.lang.Float.compare(lhs.confidence, rhs.confidence) })


        for (i in labelList.indices) {
            val r = Recognition(i.toString(),
                    labelList[i], (labelProbArray[0][i] and 0xff) / 255.0f)
            sortedLabels.add(r)
            if (r.confidence > 0) {
                Log.d("ImageRecognition", r.toString())
            }
            if (sortedLabels.size > RESULTS_TO_SHOW) {
                sortedLabels.poll()
            }
        }

        val results = ArrayList<Recognition>(RESULTS_TO_SHOW)
        for (r in sortedLabels) {
            results.add(0, r)
        }

        return results
    }

    /** 画像データを `ByteBuffer` に書き込みます  */
    fun convertBitmapToByteBuffer(bitmap: Bitmap, intValues: IntArray, imgData: ByteBuffer?) {
        if (imgData == null) return

        imgData.rewind()
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0,
                bitmap.width, bitmap.height)
        // 画像ピクセルをTensorflowモデルの予想される入力と一致するバイトバッファ表現に
        // エンコードする
        var pixel = 0
        for (i in 0 until bitmap.width) {
            for (j in 0 until bitmap.height) {
                val `val` = intValues[pixel++]
                imgData.put((`val` shr 16 and 0xFF).toByte())
                imgData.put((`val` shr 8 and 0xFF).toByte())
                imgData.put((`val` and 0xFF).toByte())
            }
        }
    }
}

private infix fun Byte.and(i: Int): Byte = this and i.toByte()

