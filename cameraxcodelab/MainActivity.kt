package com.example.cameraxcodelab

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {

    companion object{
        const val TAG = "CameraXBasic"
        const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        const val  REQUEST_CODE_PERMISSIONS = 10
        val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
    }

    private var imageCapture:ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //validacion de permisos grabadora .fuente:Geekepedia de ernesto
        if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.CAMERA), 1000)
        }

        // solicitud de los permisos de camara .fuente:codelab CameraX
        if (allPermissionGranted()){
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        //escuchador para tomar la foto
        camera_capture_button.setOnClickListener{takePhoto()}

        filmarbtn.setOnClickListener { grabar() }

        //camera_capture_button.setOnLongClickListener{ grabar() }

        outputDirectory = getoutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    //grabar video.fuente:Geekepedia de ernesto
    val REQUEST_VIDEO_CAPTURE = 1
    fun grabar(){
        Intent(MediaStore.ACTION_VIDEO_CAPTURE).also { takeVideoIntent ->
            takeVideoIntent.resolveActivity(packageManager)?.also {
                startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE)
            }
        }
    }

    fun takePhoto(){
        //obtenemos una referencia de los casos de uso de image capture
        val imageCapture = imageCapture ?: return

        //creamos un archivo para contener la imagen, con una marca de tiempo para que el nombre sea unico
        var photoFile = File(outputDirectory, (SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())) + ".jpg")
        //photoFile.appendText("/Download/photo.jpg")

        //creaos un objeto OutputFileOptions donde especificamos como queremos que sea la salida
        //guardamos la salida en el archivo que acabamos de crear(photFile)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()


        imageCapture.takePicture(
                outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
            }

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                val msg = "Photo capture succeeded: $savedUri"
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                Log.d(TAG, msg)
            }
        }
        )

    }

    fun startCamera(){
        // Used to bind the lifecycle of cameras to the lifecycle owner
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            //usado para unir el ciclo de vida de camara con el del usuario
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(viewFinder.surfaceProvider) }

            //obtenemos una vista previa
            imageCapture = ImageCapture.Builder().build()

            //analiza la luminisencia y tira la info en forma de double en el log
/*
            val imageAnalyzer = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { Log.d(TAG, "Average luminosity: $it")})
            }
*/
            //GIRAR CAMARA
            var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            changeCamBtn.setOnClickListener {
                changeCamBtn.visibility = View.GONE
                changeCamBtn2.visibility = View.VISIBLE
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                restartCamera(cameraProvider, cameraSelector, preview)
            }

            changeCamBtn2.setOnClickListener {
                changeCamBtn.visibility = View.VISIBLE
                changeCamBtn2.visibility = View.GONE
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                restartCamera(cameraProvider, cameraSelector, preview)
            }

            // Selecciona la camara trasera por defecto
            //val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            //restartCamera(cameraProvider, cameraSelector, preview)

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Une los casos de uso con la camara // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture/* imageAnalyzer*/)
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    fun allPermissionGranted() = REQUIRED_PERMISSIONS.all{
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    fun getoutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS){
            if (allPermissionGranted()){
                startCamera()
            } else {
                Toast.makeText(this, "Permisos no aceptados por el usuario", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    fun restartCamera(cameraProvider: ProcessCameraProvider, cameraSelector: CameraSelector, preview: Preview){
        try {
        // Unbind use cases before rebinding
        cameraProvider.unbindAll()

        // Une los casos de uso con la camara // Bind use cases to camera
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture /*imageAnalyzer*/)
        } catch (e: Exception){
        Log.e(TAG, "Use case binding failed", e)
        }
    }

/*
    fun grabar(){
        val recorder = Recorder.Builder
    }
*/

/*
    fun changeCamera(){
        val cameraProvideFuture = ProcessCameraProvider.getInstance(this)
        val cameraProvider = cameraProvideFuture.get()
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        val imageCapture = imageCapture ?: return
        // Preview
        val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
        }catch (e: Exception){
        Log.e(TAG, "Error change camera", e)

        }
    }
*/
}