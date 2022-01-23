package com.example.cameraxcodelab

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

class LuminosityAnalyzer(private val listener: LumaListener) :ImageAnalysis.Analyzer{

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()  //regresa el buffer a 0
        val data = ByteArray(remaining())
        get(data) //copia el buffer dentro del array
        return data //retorna el array de bytes

    }

    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        val pixels = data.map { it.toInt() and 0xFF}
        val luma = pixels.average()

        listener(luma)

        image.close()
    }
}