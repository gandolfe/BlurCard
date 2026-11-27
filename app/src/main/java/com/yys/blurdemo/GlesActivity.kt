package com.yys.blurdemo

import android.os.Bundle
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
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