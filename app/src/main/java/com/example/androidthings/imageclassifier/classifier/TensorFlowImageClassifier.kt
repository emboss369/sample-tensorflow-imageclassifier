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
import android.os.SystemClock
import android.util.Log

import org.tensorflow.lite.Interpreter

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TensorFlowを使用して画像にラベルを付けることに特化した分類器。
 */
class TensorFlowImageClassifier
/**
 * イメージを分類するためのTensorFlow Liteセッションを初期化します。
 */
@Throws(IOException::class)
constructor(context: Context, inputImageWidth: Int, inputImageHeight: Int) {

    /** TensorFlowモデルが訓練されているカテゴリのラベル。  */
    private val labels: List<String>

    /** 画像データを保持するキャッシュ。  */
    private var imgData: ByteBuffer

    /** 推論結果（Tensorflow Lite出力）。  */
    private var confidencePerLabel: Array<ByteArray>

    /** 中間ビットマップピクセルの事前割り当てバッファ  */
    private val intValues: IntArray

    /** TensorFlow Liteエンジン  */
    private val tfLite: Interpreter

    init {
        this.tfLite = Interpreter(TensorFlowHelper.loadModelFile(context, MODEL_FILE))
        this.labels = TensorFlowHelper.readLabels(context, LABELS_FILE)

        imgData = ByteBuffer.allocateDirect(
                DIM_BATCH_SIZE * inputImageWidth * inputImageHeight * DIM_PIXEL_SIZE)
        imgData.order(ByteOrder.nativeOrder())
        confidencePerLabel = Array(1) { ByteArray(labels.size) }

        // イメージピクセルのバッファを事前に割り当てる。
        intValues = IntArray(inputImageWidth * inputImageHeight)
    }

    /**
     * クラシファイアが使用するリソースをクリーンアップします。
     */
    fun destroyClassifier() {
        tfLite.close()
    }


    /**
     * @param image 分類される画像を含むビットマップ。 画像はどのようなサイズでもよいが、
     * 時間と電力を消費する可能性のある分類プロセスによって
     * 予測されるフォーマットにサイズ変更するための
     * 前処理が行われることがある。
     */
    fun doRecognize(image: Bitmap): Collection<Recognition> {
        TensorFlowHelper.convertBitmapToByteBuffer(image, intValues, imgData)

        val startTime = SystemClock.uptimeMillis()
        // Here's where the magic happens!!!
        tfLite.run(imgData, confidencePerLabel)
        val endTime = SystemClock.uptimeMillis()
        Log.d(TAG, "Timecost to run model inference: " + java.lang.Long.toString(endTime - startTime))

        // 信頼性の高い結果を得て、ラベルにマップする
        return TensorFlowHelper.getBestResults(confidencePerLabel, labels)
    }

    companion object {

        private const val TAG = "TFImageClassifier"

        private const val LABELS_FILE = "labels.txt"
        private const val MODEL_FILE = "mobilenet_quant_v1_224.tflite"

        /** 入力の次元。  */
        private const val DIM_BATCH_SIZE = 1
        private const val DIM_PIXEL_SIZE = 3
    }

}
