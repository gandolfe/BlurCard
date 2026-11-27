package com.yys.blurdemo

import android.app.Instrumentation
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.yys.blurdemo.gles.BlurView

class GlesActivity : AppCompatActivity() {
    
    
//    lateinit var imageView: ImageView
//    
//    val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()){uri: Uri? ->
//        imageView.setImageURI(uri)
//    }
    
    
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
        setContentView(R.layout.activity_gles)
        
        val background = findViewById<RelativeLayout>(R.id.mybackground_layout)
        val blurCard = findViewById<BlurView>(R.id.blurcard) 

        blurCard.setBlur(background)
        
    }
}