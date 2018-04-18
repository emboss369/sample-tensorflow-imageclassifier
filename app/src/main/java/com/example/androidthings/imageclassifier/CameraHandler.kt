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
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.util.Log
import android.view.Surface

class CameraHandler// 遅延ロードされたシングルトンなので、カメラのインスタンスは1つだけ作成されます。
private constructor() {
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var initialized: Boolean = false

    /**
     * [ImageReader] は静止画キャプチャを処理します。
     */
    private var mImageReader: ImageReader? = null


    /**
     * デバイス状態の変更を処理するコールバック
     */
    private val mStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            Log.d(TAG, "Opened camera.")
            mCameraDevice = cameraDevice
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            Log.d(TAG, "Camera disconnected, closing.")
            closeCaptureSession()
            cameraDevice.close()
        }

        override fun onError(cameraDevice: CameraDevice, i: Int) {
            Log.d(TAG, "Camera device error, closing.")
            closeCaptureSession()
            cameraDevice.close()
        }

        override fun onClosed(cameraDevice: CameraDevice) {
            Log.d(TAG, "Closed camera, releasing")
            mCameraDevice = null
        }
    }

    /**
     * セッション状態の変更を処理するコールバック
     */
    private val mSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
            // The camera is already closed
            if (mCameraDevice == null) {
                return
            }
            // When the session is ready, we start capture.
            mCaptureSession = cameraCaptureSession
            triggerImageCapture()
        }

        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
            Log.w(TAG, "Failed to configure camera")
        }
    }

    /**
     * キャプチャセッションイベント処理のコールバック
     */
    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(session: CameraCaptureSession,
                                         request: CaptureRequest,
                                         partialResult: CaptureResult) {
            Log.d(TAG, "Partial result") // 部分的な結果
        }

        override fun onCaptureCompleted(session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult) {
            session.close()
            mCaptureSession = null
            Log.d(TAG, "CaptureSession closed")
        }
    }


    /**
     * カメラデバイスを初期化する
     */
    @SuppressLint("MissingPermission")
    fun initializeCamera(context: Context, previewWidth: Int, previewHeight: Int,
                         backgroundHandler: Handler,
                         imageAvailableListener: ImageReader.OnImageAvailableListener) {
        if (initialized) {
            throw IllegalStateException(
                    "CameraHandler is already initialized or is initializing") // CameraHandlerは既に初期化されているか、初期化中です。
        }
        initialized = true

        // カメラのインスタンスを発見する
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var camIds: Array<String>? = null
        try {
            camIds = manager.cameraIdList
        } catch (e: CameraAccessException) {
            Log.w(TAG, "Cannot get the list of available cameras", e) // 利用可能なカメラのリストを取得できません
        }

        if (camIds == null || camIds.size < 1) {
            Log.d(TAG, "No cameras found") // カメラが見つかりません。
            return
        }
        Log.d(TAG, "Using camera id " + camIds[0])

        // イメージプロセッサを初期化する
        mImageReader = ImageReader.newInstance(previewWidth, previewHeight, ImageFormat.JPEG,
                MAX_IMAGES)
        mImageReader!!.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)

        // カメラリソースを開く
        try {
            manager.openCamera(camIds[0], mStateCallback, backgroundHandler)
        } catch (cae: CameraAccessException) {
            Log.d(TAG, "Camera access exception", cae)
        }

    }

    /**
     * 静止画キャプチャを開始する
     */
    fun takePicture() {
        if (mCameraDevice == null) {
            Log.w(TAG, "Cannot capture image. Camera not initialized.") // イメージをキャプチャできません。 カメラが初期化されていません。
            return
        }
        // 静止画をキャプチャするためのCameraCaptureSessionを作成します。
        try {
            mCameraDevice?.createCaptureSession(
                    listOf<Surface>(mImageReader!!.surface),
                    mSessionCallback, null)
        } catch (cae: CameraAccessException) {
            Log.e(TAG, "Cannot create camera capture session", cae) // カメラキャプチャセッションを作成できません
        }

    }

    /**
     * アクティブなセッション内で新しいキャプチャ要求を実行する
     */
    private fun triggerImageCapture() {
        try {
            val captureBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(mImageReader!!.surface)
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            Log.d(TAG, "Capture request created.")
            mCaptureSession!!.capture(captureBuilder.build(), mCaptureCallback, null)
        } catch (cae: CameraAccessException) {
            Log.e(TAG, "Cannot trigger a capture request")
        }

    }

    private fun closeCaptureSession() {
        if (mCaptureSession != null) {
            try {
                mCaptureSession!!.close()
            } catch (ex: Exception) {
                Log.w(TAG, "Could not close capture session", ex)
            }

            mCaptureSession = null
        }
    }

    /**
     * カメラのリソースを閉じる
     */
    fun shutDown() {
        try {
            closeCaptureSession()
            if (mCameraDevice != null) {
                mCameraDevice!!.close()
            }
            mImageReader!!.close()
        } finally {
            initialized = false
        }
    }

    companion object {
        private val TAG = "CameraHandler"

        private val MAX_IMAGES = 1


        private var me: CameraHandler? = null

        fun getInstance(): CameraHandler {
            me = CameraHandler()
            return me!!
        }


        /**
         * 便利なデバッグ方法：サポートされているすべてのカメラ形式をログにダンプします。
         * 通常の操作でこれを実行する必要はありませんが、このコードを
         * 別のハードウェアに移植すると非常に便利です。
         */
        fun dumpFormatInfo(context: Context) {
            // カメラのインスタンスを発見する
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var camIds: Array<String>? = null
            try {
                camIds = manager.cameraIdList
            } catch (e: CameraAccessException) {
                Log.w(TAG, "Cannot get the list of available cameras", e)
            }

            if (camIds == null || camIds.size < 1) {
                Log.d(TAG, "No cameras found")
                return
            }
            Log.d(TAG, "Using camera id " + camIds[0])
            try {
                val characteristics = manager.getCameraCharacteristics(camIds[0])
                val configs = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                for (format in configs!!.outputFormats) {
                    Log.d(TAG, "Getting sizes for format: $format")
                    for (s in configs.getOutputSizes(format)) {
                        Log.d(TAG, "\t" + s.toString())
                    }
                }
                val effects = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)
                for (effect in effects!!) {
                    Log.d(TAG, "Effect available: $effect")
                }
            } catch (e: CameraAccessException) {
                Log.d(TAG, "Cam access exception getting characteristics.")
            }

        }
    }

}