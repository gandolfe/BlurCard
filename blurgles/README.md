### 整体思路：
针对于传统View的高斯模糊解决方案。对View做一次高斯模糊并缓存模糊后的整体图像，移动毛玻璃浮控件的时候动态位移并绘制。

### 效果：
<img width="270" height="590" alt="Screenshot_20261127_162204" src="https://github.com/user-attachments/assets/c972b40a-bc00-4aa4-a619-b79f698d4175" />


### 详细流程:
创建一个空的```Bitmap```，它会被用作图像缓存，后续高斯模糊也是操作这个缓存数据；

创建一个```Canvas```，并关联这个Bitmap，把背景控件使用此Canvas绘制，那么最终内容就会缓存到这个Bitmap上；

接下来是创建Java层的```OpenGL ES```的运行环境：
创建单独的egl渲染线程，保证后续的整个OpenGL ES的运行是在同一个线程中；
创建```EGL```上下文环境，与GPU建立会话；

创建一块离屏的```Surface```缓存，后面GLES就会绘制到这里；

创建顶点着色器和片元着色器，并关联成程序；

创建2D纹理，并把之前缓存的Bitmap传给GPU；

调用GPU做水平模糊和垂直模糊，做高斯模糊；

高斯模糊完成后，从GPU缓存中读回到CPU内存中，它也是一个Bitmap；

毛玻璃悬浮控件使用模糊后的Bitmap来绘制自己的背景，根据自身跟底部背景控件的相对位移，通过```canvas.translate```移动坐标系，绘制对应的区域，让模糊的那一块刚好和它遮挡的底部区域一致。

### 数据流：
底部控件 ->canvas绘制 ->缓存Bitmap（Cache_Bitmap）-> upload到2D纹理 ->GPU做高斯模糊 

->GPU的离屏buffer ->写回到CPU -> 新的缓存Bitmap(Blured_Bitmap) ->毛玻璃悬浮控件使用Blured_Bitmap绘制背景

->毛玻璃悬浮控件移动 ->canvas做translate偏移 ->再次使用Blured_Bitmap绘制背景
