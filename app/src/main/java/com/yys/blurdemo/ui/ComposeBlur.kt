package com.yys.blurdemo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yys.blurdemo.R
import kotlin.math.roundToInt

@OptIn(ExperimentalGraphicsApi::class)
@Composable
fun ComposeBlur(modifier: Modifier = Modifier) {
    // 背景的离屏图层：卡片用它"镜像"重绘身后的内容
    val backgroundLayer = rememberGraphicsLayer()
    var backgroundPosition by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.festival_qingming),
            contentDescription = "背景",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { backgroundPosition = it.positionInRoot() } //背景图左上角相对于根容器的绝对坐标
                .drawWithContent {
                    backgroundLayer.record { this@drawWithContent.drawContent() } //把背景图绘制到离屏缓存中
                    drawContent()  //绘制自己本身到屏幕上
                }
        )

        DraggableGlassCard(backgroundLayer, backgroundPosition)
    }
}

@OptIn(ExperimentalGraphicsApi::class)
@Composable
fun DraggableGlassCard(
    backgroundLayer: GraphicsLayer,
    backgroundPosition: Offset
) {
    var offsetX by remember { mutableStateOf(150f) }
    var offsetY by remember { mutableStateOf(150f) }
    var cardPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(290.dp)
            .height(90.dp)
            .onGloballyPositioned { cardPosition = it.positionInRoot() }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->  //这里的detectDragGestures是拖拽手势的处理
                    change.consume()  //消费事件
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        // 毛玻璃层：把背景图层平移到卡片位置重绘，并对卡片显示区域模糊
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {   //使用独立渲染层，使用GPU做特效变换
                    renderEffect = BlurEffect(
                        radiusX = 25.dp.toPx(),
                        radiusY = 25.dp.toPx()
                    )
                }
                .drawBehind {
                    translate(  //让底层的背景图，反向移动，让绘制的位置和卡片框所处的全局位置一致
                        backgroundPosition.x - cardPosition.x,
                        backgroundPosition.y - cardPosition.y
                    ) {
                        drawLayer(backgroundLayer)  //真正绘制底图
                    }
                }
                .background(Color.White.copy(alpha = 0.15f))
        )

        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp)) {
            Text(
                text = "毛玻璃悬浮窗",
                color = Color.White
            )

            Text(
                text = "按住卡片任意位置可拖拽",
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
