package com.yys.blurdemo.gles

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.util.Log

/**
 * 基于 EGL14 的离屏渲染环境。
 *
 * EGL 负责"摆好画架"，GLES20 负责"作画"，两者分工：
 * - [EglCore]：与 GPU 建立会话（Display）、挑选像素格式（Config）、创建渲染上下文（Context）。
 *   这些对象与具体图片尺寸无关，**创建一次后整个生命周期复用**。
 * - [PbufferSurface]：一块不显示到屏幕的"隐形画布"，尺寸与图片一致，每张图创建/销毁一次。
 *
 * 线程模型：EGL 上下文绑定线程。一个 Context 同一时刻只能 makeCurrent 到一个线程，
 * 所有 GL 调用必须在同一线程上执行（本包由 GlGaussianBlurProcessor 固定在专用单线程中调用）。
 *
 * @param shareContext 需要跨 Context 共享纹理/着色器时传入，离屏模糊场景传 null 即可
 */
internal class EglCore(shareContext: EGLContext? = null) {

    private val display: EGLDisplay
    private val config: EGLConfig
    private val context: EGLContext
    private var released = false

    init {
        // 1. 连接默认 GPU 设备
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display !== EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }

        // 2. 初始化会话（返回 EGL 版本号，这里不关心）
        val major = IntArray(1)
        val minor = IntArray(1)
        check(EGL14.eglInitialize(display, major, 0, minor, 0)) { "eglInitialize failed" }

        // 3. 挑选像素格式：支持 GLES2 渲染、支持 PBuffer 离屏画布、RGBA 各 8bit
        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, configs.size, numConfigs, 0)
                    && numConfigs[0] > 0 && configs[0] != null
        ) { "eglChooseConfig failed" }
        config = configs[0]!!

        // 4. 创建渲染上下文，声明使用 OpenGL ES 2.0
        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(
            display, config, shareContext ?: EGL14.EGL_NO_CONTEXT, contextAttribs, 0
        )
        check(context !== EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
    }

    /**
     * 创建一块 width x height 的离屏像素缓冲区（PBuffer）。
     * 调用 [PbufferSurface.makeCurrent] 后，当前线程的所有 GLES 调用都会绘制到它上面。
     */
    fun createPbufferSurface(width: Int, height: Int): PbufferSurface {
        val attribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        val surface = EGL14.eglCreatePbufferSurface(display, config, attribs, 0)
        check(surface !== EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }
        return PbufferSurface(surface)
    }

    fun release() {
        if (released) return
        released = true
        // 先解绑当前线程，再销毁上下文
        EGL14.eglMakeCurrent(
            display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
        )
        EGL14.eglDestroyContext(display, context)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(display)
    }

    /**
     * 离屏像素缓冲区。持有 EGLSurface 句柄，负责 makeCurrent / 解绑 / 销毁。
     */
    inner class PbufferSurface(private val surface: EGLSurface) {

        private var released = false

        /** 把 Context 绑定到当前线程，之后的 GLES 调用都画到这块 PBuffer 上 */
        fun makeCurrent() {
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }
        }

        /** 当前线程解绑，然后销毁这块画布 */
        fun release() {
            if (released) return
            released = true
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(display, surface)
        }
    }

    companion object {
        private const val TAG = "EglCore"
    }
}
