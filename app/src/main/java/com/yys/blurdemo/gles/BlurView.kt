package com.yys.blurdemo.gles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    val SCALE_SAMPLE = 5
    
    val processor: GlGaussianBlurProcessor = GlGaussianBlurProcessor(radius = 10, sampleFactor = 1.0f)
    
    var bluredView : View? = null
    
    private var blurBitmap: Bitmap? = null
    
    private var blurCanvas: Canvas? = null
    
    private var isBlured = false
    
    fun setBlur(view: View){
        bluredView = view
        isBlured = false
    }
    
    fun refresh(){
        startBlured()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isBlured) {
            canvas.save()
            canvas.translate(bluredView!!.x - this.x, bluredView!!.y - this.y)
            canvas.scale(SCALE_SAMPLE.toFloat(), SCALE_SAMPLE.toFloat())
            canvas.drawBitmap(blurBitmap!!,0f, 0f, null)
            canvas.restore()
        } else {
            startBlured()
        }
    }

    
    /**
     * 开始做高斯模糊处理
     */
    private fun startBlured(){
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            withContext(Dispatchers.Default){
                if (bluredView == null) {
                    return@withContext
                }
                if (blurCanvas == null) {
                    val scaleWidth = bluredView!!.width / SCALE_SAMPLE
                    val scaleHeight = bluredView!!.height / SCALE_SAMPLE
                    blurBitmap = Bitmap.createBitmap(scaleWidth, scaleHeight, Bitmap.Config.ARGB_8888)
                    blurCanvas = Canvas(blurBitmap!!)
                    blurCanvas?.scale(1.0f/SCALE_SAMPLE, 1.0f/SCALE_SAMPLE)
                }
                bluredView?.draw(blurCanvas!!)
                blurBitmap = processor.blur(blurBitmap!!)
                isBlured = true
            }
            invalidate()
        }
    }

    
    var oldX: Float = 0f
    var oldY: Float = 0f
    
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        
        when(event?.actionMasked){
            MotionEvent.ACTION_DOWN -> {
                oldX = event.rawX
                oldY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val offsetX = event.getRawX() - oldX
                val offsetY = event.getRawY() - oldY
                offsetLeftAndRight(offsetX.toInt())
                offsetTopAndBottom(offsetY.toInt())
                oldX = event.rawX
                oldY = event.rawY
                invalidate()
            }
        }
        
        return super.onTouchEvent(event)
    }
}