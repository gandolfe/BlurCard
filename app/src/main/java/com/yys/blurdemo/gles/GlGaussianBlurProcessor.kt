package com.yys.blurdemo.gles

import android.graphics.Bitmap
import android.opengl.GLES20
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * 用 Kotlin + EGL14 + OpenGL ES 2.0 实现的高斯模糊处理器。
 * 
 * Bitmap(CPU) ──上传──▶ GPU纹理 ──两趟分离式模糊──▶ PBuffer 离屏画布 ──读回──▶ Bitmap(CPU)
 * 
 *
 * - 第一趟（水平）：原图纹理 → FBO 附着的中间纹理
 * - 第二趟（垂直）：中间纹理 → PBuffer 默认画布
 * - glReadPixels 把画布像素读回 CPU，写进输出 Bitmap
 *
 * 线程模型：EGL Context 绑定线程，所有 GL 调用必须串行。
 * 本类内部用一个专用单线程执行全部 GL 操作，因此可以从任意线程调用 [blur]，
 * 结果以同步方式返回（阻塞等待）。
 *
 * @param radius 模糊半径（单侧采样点数），建议 1~25；越大越模糊也越慢
 * @param sampleFactor 内部降采样倍数（与 HokoBlur 的 sampleFactor 含义一致）：
 *        1.0 表示不缩放；N(>1) 表示先把图缩小 N 倍再模糊，最后放大回原尺寸。
 *        像素量按 N² 级下降，同时模糊半径在视觉上被放大 N 倍，这是"又快又糊"的关键
 */
class GlGaussianBlurProcessor(
    private val radius: Int = DEFAULT_RADIUS,
    private val sampleFactor: Float = 1.0f
) : Closeable {

    // 创建单独线程，保证EGL上下文的运行环境在同一个线程
    private val glThread: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gl-blur-thread").apply { isDaemon = true }
    }

    /** GL 环境（Display/Config/Context）只在专用 GL 线程上首次使用时创建，之后复用 */
    private var eglCore: EglCore? = null
    private var quad: GlObjects.FullScreenQuad? = null
    private var program: GlObjects.Program? = null

    /** 执行一次高斯模糊，返回模糊后的新 Bitmap，输入 Bitmap 不会被修改。 */
    fun blur(input: Bitmap): Bitmap = submit {
        doBlur(input)
    }.get()

    /** 异步版本，立即返回 Future。 */
    fun blurAsync(input: Bitmap): Future<Bitmap> = submit { doBlur(input) }

    /** 释放 EGL 环境与全部 GL 资源，之后不可再调用 blur。 */
    override fun close() {
        glThread.submit {
            program?.release(); program = null
            quad = null
            eglCore?.release(); eglCore = null
        }.get()
        glThread.shutdown()
    }

    private fun <T> submit(task: Callable<T>): Future<T> = glThread.submit(task)

    // ------------------------------------------------------------------
    // 以下所有方法都只在 glThread 上执行
    // ------------------------------------------------------------------

    private fun doBlur(input: Bitmap): Bitmap {
        require(!input.isRecycled) { "input bitmap is recycled" }
        val width = input.width
        val height = input.height
        check(width > 0 && height > 0) { "invalid bitmap size: ${width}x$height" }

        // sampleFactor = N 表示先缩小 N 倍再模糊，最后放大回原尺寸；1.0 表示原尺寸
        val scale = sampleFactor.coerceAtLeast(1.0f)
        val scaled = if (scale > 1.0f) {
            val scaledWidth = (width / scale).toInt().coerceAtLeast(1)
            val scaledHeight = (height / scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(input, scaledWidth, scaledHeight, true)
        } else {
            input
        }

        ensureEglCore()

        // 每张图创建一次与图片同尺寸的离屏画布，用完即销毁
        val surface = eglCore!!.createPbufferSurface(scaled.width, scaled.height)
        var inputTexture: GlObjects.Texture? = null
        var intermediateTexture: GlObjects.Texture? = null
        var frameBuffer: GlObjects.FrameBuffer? = null
        try {
            surface.makeCurrent()
            // 关键：glCreateProgram/glCreateShader 必须在已有 current context 的线程上调用，
            // 所以着色器程序必须等 makeCurrent 之后才能创建，否则返回 0 直接崩
            ensureGlObjects()
            val program = this.program!!
            val quad = this.quad!!

            // 1. 准备输入纹理与中间纹理（中间纹理由 FBO 写入）
            inputTexture = GlObjects.Texture(scaled.width, scaled.height).apply { upload(scaled) }
            intermediateTexture = GlObjects.Texture(scaled.width, scaled.height)
            frameBuffer = GlObjects.FrameBuffer(intermediateTexture)

            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)

            // 2. 第一趟：水平模糊，原图 → FBO（结果存进 intermediateTexture）
            GLES20.glViewport(0, 0, scaled.width, scaled.height)
            frameBuffer.bind()
            drawPass(program, quad, inputTexture, radius, widthStep = 1f / scaled.width, heightStep = 0f)

            // 3. 第二趟：垂直模糊，中间纹理 → 默认画布（PBuffer）
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            drawPass(program, quad, intermediateTexture, radius, widthStep = 0f, heightStep = 1f / scaled.height)

            // 4. 等 GPU 全部执行完，然后读回 CPU
            GLES20.glFinish()
            val output = readPixelsToBitmap(scaled.width, scaled.height)

            // 5. 若做过降采样，把结果放大回原尺寸
            return if (scale > 1.0f) {
                Bitmap.createScaledBitmap(output, width, height, true).also { output.recycle() }
            } else {
                output
            }
        } finally {
            // 6. 逐帧资源全部释放（纹理/FBO/Surface），Display/Context 保留复用
            frameBuffer?.release()
            inputTexture?.release()
            intermediateTexture?.release()
            surface.release()
        }
    }

    /** 一趟一维模糊：绑 program、传顶点和 uniform、画全屏四边形 */
    private fun drawPass(
        program: GlObjects.Program,
        quad: GlObjects.FullScreenQuad,
        inputTexture: GlObjects.Texture,
        radius: Int,
        widthStep: Float,
        heightStep: Float
    ) {
        program.use()
        program.enableAttributes()
        program.setPositions(quad.vertices)
        program.setTexCoords(quad.texCoordsBuffer)
        program.setUniforms(textureUnit = 0, inputTexture, radius, widthStep, heightStep)

        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES, quad.indices.capacity(),
            GLES20.GL_UNSIGNED_SHORT, quad.indices
        )

        // 复位状态，避免影响下一趟
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    /** 把当前绑定画布（PBuffer）的像素读回 CPU，并写入一张新 Bitmap */
    private fun readPixelsToBitmap(width: Int, height: Int): Bitmap {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN)
        // GPU → CPU 拷贝，按 RGBA 每像素 4 字节读出
        GLES20.glReadPixels(
            0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
        )
        buffer.rewind()

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.copyPixelsFromBuffer(buffer)
        return output
    }

    /** 懒创建 EGL 环境（Display/Config/Context）。创建它们不依赖 current context，可提前调用 */
    private fun ensureEglCore() {
        if (eglCore == null) {
            eglCore = EglCore()
        }
    }

    /** 懒创建 GL 对象（顶点数据、着色器程序）。必须在 makeCurrent 之后调用（编译 shader 需要 current context） */
    private fun ensureGlObjects() {
        if (quad == null) {
            quad = GlObjects.FullScreenQuad()
        }
        if (program == null) {
            program = GlObjects.Program(Shaders.VERTEX_SHADER, Shaders.FRAGMENT_GAUSSIAN)
        }
    }

    companion object {
        private const val TAG = "GlGaussianBlur"
        private const val DEFAULT_RADIUS = 10
    }
}
