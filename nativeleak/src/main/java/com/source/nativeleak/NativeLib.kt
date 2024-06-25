package com.source.nativeleak

class NativeLib {

    /**
     * A native method that is implemented by the 'nativeleak' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'nativeleak' library on application startup.
        init {
            System.loadLibrary("nativeleak")
        }
    }
}