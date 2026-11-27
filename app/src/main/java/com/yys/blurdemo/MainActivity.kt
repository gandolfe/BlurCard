package com.yys.blurdemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.yys.blurdemo.ui.ComposeBlur
import com.yys.blurdemo.ui.theme.BlurDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlurDemoTheme {
                
                val context = LocalContext.current
                val second = remember { mutableStateOf(0) }

                BackHandler(enabled = true) { 
                    if (second.value == 2){
                        second.value = 0
                    }else{
                        finish()
                    }
                }
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (second.value == 2){
                        ComposeBlur(modifier = Modifier.padding(innerPadding).fillMaxSize())
                    }else{
                        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxHeight()) {

                            Button(onClick = {
                                val intent = Intent(context, GlesActivity::class.java)
                                startActivity(intent)
                            }) {
                                Text("OpenGl ES 方式模糊")
                            }

                            Button(onClick = {second.value = 2}) {
                                Text("Compose原生高斯模糊")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BlurDemoTheme {
        Greeting("Android")
    }
}