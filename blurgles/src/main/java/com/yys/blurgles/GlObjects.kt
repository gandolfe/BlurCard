package com.yys.blurdemo.gles

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * 三个 GL 对象的轻量封装：纹理（Texture）、帧缓冲（FrameBuffer）、着色器程序（Program）。
 * 都要求在 EGL 上下文已 makeCurrent 的线程上创建和使用。
 */
internal object GlObjects {

    /**
     * GPU 纹理。GL 不能直接绘制 Bitmap，必须先把像素上传成纹理。
     */
    internal class Texture(width: Int, height: Int) {
        val id: Int = glGenTexture()
        private val size: Int = width * height

        init {
            bind()
            // 纹理过滤：LINEAR 双线性插值，采样点落在整数格之间时平滑过渡
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            // 纹理坐标越界时钳制到边缘（模糊时边缘采样会越界）
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            // 预分配显存（数据稍后由 uploadBitmap 填充）
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, ByteBuffer.allocateDirect(size * 4)
            )
        }

        fun bind() {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        }

        /** 把 Bitmap 像素上传到 GPU（内部同样走 glTexImage2D，只是替我们处理好了像素格式） */
        fun upload(bitmap: Bitmap) {
            bind()
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        }

        fun release() {
            GLES20.glDeleteTextures(1, intArrayOf(id), 0)
        }

        private fun glGenTexture(): Int {
            val handles = IntArray(1)
            GLES20.glGenTextures(1, handles, 0)
            return handles[0]
        }
    }

    /**
     * 帧缓冲对象（FBO）：把渲染目标从默认画布重定向到一张纹理上。
     * 有了它，第一趟水平模糊的结果才能存进纹理，供第二趟垂直模糊继续使用。
     */
    internal class FrameBuffer(private val texture: Texture) {
        val id: Int = glGenFrameBuffer()

        init {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, id)
            // 把纹理挂载为 FBO 的颜色输出：之后画的所有像素都写进这张纹理
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texture.id, 0
            )
            check(
                GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
            ) { "Framebuffer incomplete" }
        }

        fun bind() {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, id)
        }

        fun release() {
            GLES20.glDeleteFramebuffers(1, intArrayOf(id), 0)
        }

        private fun glGenFrameBuffer(): Int {
            val handles = IntArray(1)
            GLES20.glGenFramebuffers(1, handles, 0)
            return handles[0]
        }
    }

    /**
     * 着色器程序：顶点着色器 + 片元着色器编译链接后的可执行体。
     */
    internal class Program(vertexSource: String, fragmentSource: String) {

        val id: Int = GLES20.glCreateProgram()
        private val positionLocation: Int
        private val texCoordLocation: Int
        private val textureLocation: Int
        private val radiusLocation: Int
        private val widthOffsetLocation: Int
        private val heightOffsetLocation: Int

        init {
            check(id != 0) { "glCreateProgram failed" }
            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            GLES20.glAttachShader(id, vertexShader)
            GLES20.glAttachShader(id, fragmentShader)
            GLES20.glLinkProgram(id)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, linkStatus, 0)
            check(linkStatus[0] == GLES20.GL_TRUE) { "glLinkProgram failed: ${GLES20.glGetProgramInfoLog(id)}" }

            // shader 对象链接后即可删除
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)

            // 缓存 attribute / uniform 的 location，绘制时直接用，避免每次查询
            positionLocation = GLES20.glGetAttribLocation(id, "aPosition")
            texCoordLocation = GLES20.glGetAttribLocation(id, "aTexCoord")
            textureLocation = GLES20.glGetUniformLocation(id, "uTexture")
            radiusLocation = GLES20.glGetUniformLocation(id, "uRadius")
            widthOffsetLocation = GLES20.glGetUniformLocation(id, "uWidthOffset")
            heightOffsetLocation = GLES20.glGetUniformLocation(id, "uHeightOffset")
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { "shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
            return shader
        }

        fun use() {
            GLES20.glUseProgram(id)
        }

        /**
         * 启用顶点属性数组。
         */
        fun enableAttributes() {
            GLES20.glEnableVertexAttribArray(positionLocation)
            GLES20.glEnableVertexAttribArray(texCoordLocation)
        }

        fun setPositions(buffer: FloatBuffer) {
            // vec4 attribute 但只传 3 个分量（x,y,z），GL 自动把 w 补成 1
            GLES20.glVertexAttribPointer(positionLocation, 3, GLES20.GL_FLOAT, false, 0, buffer)
        }

        fun setTexCoords(buffer: FloatBuffer) {
            GLES20.glVertexAttribPointer(texCoordLocation, 2, GLES20.GL_FLOAT, false, 0, buffer)
        }

        fun setUniforms(textureUnit: Int, texture: Texture, radius: Int, widthOffset: Float, heightOffset: Float) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + textureUnit)
            texture.bind()
            GLES20.glUniform1i(textureLocation, textureUnit)
            GLES20.glUniform1i(radiusLocation, radius)
            GLES20.glUniform1f(widthOffsetLocation, widthOffset)
            GLES20.glUniform1f(heightOffsetLocation, heightOffset)
        }

        fun release() {
            GLES20.glDeleteProgram(id)
        }
    }

    /**
     * 全屏四边形的顶点 / 索引 / 纹理坐标数据。
     * GL 只能画三角形，四边形要拆成两个三角形（0,1,2 / 0,2,3）。
     * GL 的裁剪空间坐标系：中心是 (0,0)，四角是 ±1，所以这 4 个顶点正好铺满整个画布。
     */
    internal class FullScreenQuad {

        // 顶点坐标（裁剪空间）：左上、左下、右下、右上
        private val vertexCoords = floatArrayOf(
            -1f, 1f, 0f,   // top left
            -1f, -1f, 0f,  // bottom left
            1f, -1f, 0f,   // bottom right
            1f, 1f, 0f     // top right
        )

        // 每个顶点对应的纹理 UV 坐标（与顶点顺序一一对应）
        private val texCoords = floatArrayOf(
            0f, 1f,  // top left
            0f, 0f,  // bottom left
            1f, 0f,  // bottom right
            1f, 1f   // top right
        )

        // 两个三角形的顶点索引
        private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)

        val vertices: FloatBuffer = toFloatBuffer(vertexCoords)
        val texCoordsBuffer: FloatBuffer = toFloatBuffer(texCoords)
        val indices: ShortBuffer = toShortBuffer(drawOrder)

        private fun toFloatBuffer(array: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(array.size * 4)  // float = 4 字节
                .order(ByteOrder.nativeOrder())        // 字节序与本机硬件一致
                .asFloatBuffer()                       // 切换为按 float 读写的视图
                .apply { put(array); position(0) }     // 灌入数据并把读指针拨回开头

        private fun toShortBuffer(array: ShortArray): ShortBuffer =
            ByteBuffer.allocateDirect(array.size * 2)  // short = 2 字节
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply { put(array); position(0) }
    }
}
