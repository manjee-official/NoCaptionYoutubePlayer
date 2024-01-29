package com.manjee.nocaptionyoutubeplayer

class NativeLib {

    /**
     * A native method that is implemented by the 'nocaptionyoutubeplayer' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'nocaptionyoutubeplayer' library on application startup.
        init {
            System.loadLibrary("nocaptionyoutubeplayer")
        }
    }
}