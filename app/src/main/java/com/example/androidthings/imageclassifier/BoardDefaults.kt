/*
 * Copyright 2016, The Android Open Source Project
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

import android.os.Build

object BoardDefaults {
    private const val DEVICE_RPI3 = "rpi3"
    private const val DEVICE_IMX6UL_PICO = "imx6ul_pico"
    private const val DEVICE_IMX7D_PICO = "imx7d_pico"

    /**
     * LEDが接続されているGPIOピンを返します。
     */
    val gpioForLED: String
        get() {
            return when (Build.DEVICE) {
                DEVICE_RPI3 -> "BCM6"
                DEVICE_IMX6UL_PICO -> "GPIO4_IO20"
                DEVICE_IMX7D_PICO -> "GPIO2_IO02"
                else -> throw IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE)
            }
        }

    /**
     * ボタンが接続されているGPIOピンを返します。
     */
    val gpioForButton: String
        get() {
            return when (Build.DEVICE) {
                DEVICE_RPI3 -> "BCM21"
                DEVICE_IMX6UL_PICO -> "GPIO4_IO20"
                DEVICE_IMX7D_PICO -> "GPIO6_IO14"
                else -> throw IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE)
            }
        }
}
