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
package com.example.androidthings.imageclassifier

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import com.example.androidthings.imageclassifier.classifier.Recognition
import com.example.androidthings.imageclassifier.classifier.TensorFlowImageClassifier
import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import kotlinx.android.synthetic.main.activity_camera.*
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("SetTextI18n")
class ImageClassifierActivity : Activity(), ImageReader.OnImageAvailableListener {

    private var mImagePreprocessor: ImagePreprocessor? = null
    private var mTtsEngine: TextToSpeech? = null
    private var mTtsSpeaker: TtsSpeaker? = null
    private var mCameraHandler: CameraHandler? = null
    private var mTensorFlowClassifier: TensorFlowImageClassifier? = null

    private lateinit var mBackgroundThread: HandlerThread
    private lateinit var mBackgroundHandler: Handler

    private val mReady = AtomicBoolean(false)
    private var mButtonDriver: ButtonInputDriver? = null
    private var mReadyLED: Gpio? = null

    private val mInitializeOnBackground = Runnable {
        mImagePreprocessor = ImagePreprocessor(PREVIEW_IMAGE_WIDTH, PREVIEW_IMAGE_HEIGHT,
                TF_INPUT_IMAGE_WIDTH, TF_INPUT_IMAGE_HEIGHT)

        mTtsSpeaker = TtsSpeaker()
        mTtsSpeaker!!.setHasSenseOfHumor(true)
        mTtsEngine = TextToSpeech(this@ImageClassifierActivity,
                TextToSpeech.OnInitListener { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        mTtsEngine?.language = Locale.US
                        mTtsEngine?.setOnUtteranceProgressListener(utteranceListener)
                        mTtsEngine?.let {
                            mTtsSpeaker?.speakReady(it)
                        }
                    } else {
                        Log.w(TAG, "Could not open TTS Engine (onInit status=" + status
                                + "). Ignoring text to speech")
                        mTtsEngine = null
                    }
                })
        mCameraHandler = CameraHandler.getInstance()
        mCameraHandler!!.initializeCamera(this@ImageClassifierActivity,
                PREVIEW_IMAGE_WIDTH, PREVIEW_IMAGE_HEIGHT, mBackgroundHandler,
                this@ImageClassifierActivity)

        try {
            mTensorFlowClassifier = TensorFlowImageClassifier(this@ImageClassifierActivity,
                    TF_INPUT_IMAGE_WIDTH, TF_INPUT_IMAGE_HEIGHT)
        } catch (e: IOException) {
            throw IllegalStateException("Cannot initialize TFLite Classifier", e)
        }

        setReady(true)
    }

    private val mBackgroundClickHandler = Runnable {
        mTtsEngine?.let {
            mTtsSpeaker!!.speakShutterSound(it)
        }
        mCameraHandler!!.takePicture()
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            setReady(false)
        }

        override fun onDone(utteranceId: String) {
            setReady(true)
        }

        override fun onError(utteranceId: String?) {
            setReady(true)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            super.onError(utteranceId, errorCode)
            setReady(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_camera)

        container.setOnClickListener {
            onShutterClick()
        }

        init()
    }

    private fun init() {
        if (isAndroidThingsDevice(this)) {
            initPIO()
        }

        mBackgroundThread = HandlerThread("BackgroundThread")
        mBackgroundThread.start()
        mBackgroundHandler = Handler(mBackgroundThread.looper)
        mBackgroundHandler.post(mInitializeOnBackground)
    }

    /**
     * This method should only be called when running on an Android Things device.
     */
    private fun initPIO() {
        val pioManager = PeripheralManager.getInstance()
        try {
            mReadyLED = pioManager.openGpio(BoardDefaults.gpioForLED)
            mReadyLED!!.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
            mButtonDriver = ButtonInputDriver(
                    BoardDefaults.gpioForButton,
                    Button.LogicState.PRESSED_WHEN_LOW,
                    SHUTTER_KEYCODE)
            mButtonDriver!!.register()
        } catch (e: IOException) {
            mButtonDriver = null
            Log.w(TAG, "Could not open GPIO pins", e)
        }

    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, "Received key up: $keyCode")
        if (keyCode == SHUTTER_KEYCODE) {
            startImageCapture()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Invoked when the user taps on the UI from a touch-enabled display
     */
    private fun onShutterClick() {
        Log.d(TAG, "Received screen tap")
        startImageCapture()
    }

    /**
     * Verify and initiate a new image capture
     */
    private fun startImageCapture() {
        Log.d(TAG, "Ready for another capture? " + mReady.get())
        if (mReady.get()) {
            setReady(false)
            resultText.text = "Hold on..."
            mBackgroundHandler.post(mBackgroundClickHandler)
        } else {
            Log.i(TAG, "Sorry, processing hasn't finished. Try again in a few seconds")
        }
    }

    /**
     * Mark the system as ready for a new image capture
     */
    private fun setReady(ready: Boolean) {
        mReady.set(ready)
        if (mReadyLED != null) {
            try {
                mReadyLED!!.value = ready
            } catch (e: IOException) {
                Log.w(TAG, "Could not set LED", e)
            }

        }
    }

    override fun onImageAvailable(reader: ImageReader) {
        var bitmap: Bitmap? = null
        reader.acquireNextImage().use { image ->
            bitmap = mImagePreprocessor?.preprocessImage(image)
        }

        if (bitmap == null) return

        runOnUiThread {
            if (bitmap != null)
                imageView?.setImageBitmap(bitmap)
        }

        val results: Collection<Recognition>? = mTensorFlowClassifier?.doRecognize(bitmap!!)

        Log.d(TAG, "Got the following results from Tensorflow: $results")

        runOnUiThread {

            if (results == null || results.isEmpty()) {
                resultText!!.text = "I don't understand what I see"
            } else {

                val sb = StringBuilder()
                val it = results.iterator()
                var counter = 0
                while (it.hasNext()) {
                    val r = it.next()
                    sb.append(r.title)
                    counter++
                    if (counter < results.size - 1) {
                        sb.append(", ")
                    } else if (counter == results.size - 1) {
                        sb.append(" or ")
                    }
                }
                resultText!!.text = sb.toString()
            }
        }

        // speak out loud the result of the image recognition
        // if theres no TTS, we don't need to wait until the utterance is spoken, so we set
        // to ready right away.

        mTtsEngine?.let {
            if (results != null)
                mTtsSpeaker?.speakResults(it, results)
        } ?: setReady(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mBackgroundThread.quit()
        } catch (t: Throwable) {
            // close quietly
        }

        try {
            if (mCameraHandler != null) mCameraHandler!!.shutDown()
        } catch (t: Throwable) {
            // close quietly
        }

        try {
            if (mTensorFlowClassifier != null) mTensorFlowClassifier!!.destroyClassifier()
        } catch (t: Throwable) {
            // close quietly
        }

        try {
            if (mButtonDriver != null) mButtonDriver!!.close()
        } catch (t: Throwable) {
            // close quietly
        }

        if (mTtsEngine != null) {
            mTtsEngine!!.stop()
            mTtsEngine!!.shutdown()
        }
    }

    /**
     * @return true if this device is running Android Things.
     *
     * Source: https://stackoverflow.com/a/44171734/112705
     */
    private fun isAndroidThingsDevice(context: Context): Boolean {
        // We can't use PackageManager.FEATURE_EMBEDDED here as it was only added in API level 26,
        // and we currently target a lower minSdkVersion
        val pm = context.packageManager
        val isRunningAndroidThings = pm.hasSystemFeature("android.hardware.type.embedded")
        Log.d(TAG, "isRunningAndroidThings: $isRunningAndroidThings")
        return isRunningAndroidThings
    }

    companion object {
        private const val TAG = "ImageClassifierActivity"

        private const val PREVIEW_IMAGE_WIDTH = 640
        private const val PREVIEW_IMAGE_HEIGHT = 480
        private const val TF_INPUT_IMAGE_WIDTH = 224
        private const val TF_INPUT_IMAGE_HEIGHT = 224

        /* Key code used by GPIO button to trigger image capture */
        private const val SHUTTER_KEYCODE = KeyEvent.KEYCODE_CAMERA
    }
}
