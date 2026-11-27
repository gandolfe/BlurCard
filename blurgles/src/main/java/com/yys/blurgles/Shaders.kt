package com.yys.blurdemo.gles

/**
 * GLSL 着色器源码（高斯模糊）。
 *
 * 把二维高斯模糊拆成"水平一趟 + 垂直一趟"两个一维模糊（分离式模糊），
 * 计算量从 O(radius^2) 降为 O(2 * radius)。
 */
internal object Shaders {

    /**
     * 顶点着色器GlObjects
     */
    const val VERTEX_SHADER: String = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """

    /**
     * 片元着色器：对当前像素沿一个方向（水平或垂直）做高斯加权平均。
     */
    const val FRAGMENT_GAUSSIAN: String = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform int uRadius;
        uniform float uWidthOffset;
        uniform float uHeightOffset;

        mediump float gaussWeight(mediump float currentPos, mediump float sigma) {
            return 1.0 / sigma * exp(-(currentPos * currentPos) / (2.0 * sigma * sigma));
        }

        void main() {
            int diameter = 2 * uRadius + 1;
            vec4 sampleTex = vec4(0, 0, 0, 0);
            vec3 col = vec3(0, 0, 0);
            float weightSum = 0.0;
            for (int i = 0; i < diameter; i++) {
                // 第 i 个采样点相对当前像素的偏移（沿 uWidthOffset / uHeightOffset 指定的方向）
                vec2 offset = vec2(float(i - uRadius) * uWidthOffset, float(i - uRadius) * uHeightOffset);
                sampleTex = vec4(texture2D(uTexture, vTexCoord.st + offset));
                float index = float(i);
                // sigma 取 (半径 + 1) / 2，让权重分布平滑、不出现明显的振铃
                float weight = gaussWeight(
                    index - float(diameter - 1) / 2.0,
                    (float(diameter - 1) / 2.0 + 1.0) / 2.0
                );
                col += sampleTex.rgb * weight;
                weightSum += weight;
            }
            // 归一化权重；alpha 直接取中心采样点的值（与 hoko-blur 行为一致）
            gl_FragColor = vec4(col / weightSum, sampleTex.a);
        }
    """
}
