package com.example.androidthings.imageclassifier.classifier

/**
 * 識別されたものを記述するClassifierによって返された不変の結果。
 */
class Recognition(
        /**
         * 認識されたものの一意の識別子。 クラスに固有であり、オブジェクトのインスタンスでは
         * ありません。
         */
        val id: String?,
        /**
         * Display name for the recognition.
         */
        val title: String?,
        /**
         * 他人との相対的な認識がどれほど良いかを示すソート可能なスコア。 高い方が良いはずです。
         */
        val confidence: Float = 0f) {


    override fun toString(): String {
        var resultString = ""
        if (id != null) {
            resultString += "[$id] "
        }

        if (title != null) {
            resultString += "$title "
        }

        resultString += String.format("(%.1f%%) ", confidence * 100.0f)

        return resultString.trim { it <= ' ' }
    }
}
