使用Compose自带的renderEfeect中的BlurEffect，实现卡片的高斯模糊效果。

## 1.实现思路

首先需要对底图创建一个离屏渲染图层`GraphicsLayer`，在绘制底图的时候，需要再往这个底图GraphicsLayer也绘制一份。

获取底图在整个根布局中的位置（控件左上角相对根布局左上角位置）backgroundPosition。

监控上层卡片的多拽动作，让卡片跟随手势拖拽移动，并且获取到卡片在根布局中最新的位置cardPosition。

卡片内部底部创建一个Box控件，作为卡片的背景容器，我们会在这个背景容器里去实现毛玻璃效果。

对这个背景容器调用`Modifier.graphicsLayer`函数，对图层启用独立渲染，并在函数的lambda表达式中增加`BlurEffect`，即高斯模糊特效。

设置完特效后，背景控件使用`drawBehind`函数，在函数里调用`drawLayer`，把底图GraphicsLayer缓存的图像绘制到当前的图层。

但是绘制的只是底图GraphicsLayer缓存图像中卡片所在位置的图像，所以需要根据backgroundPosition和cardPosition计算出底图GraphicsLayer缓存图像的偏移量。

相当于是把底图GraphicsLayer缓存里扣出一块放进卡片背景容器。

卡片背景容器绘制完成后，会把自己的graphicsLayer整体做一个BlurEffect。这样卡片的高斯模糊背景，就会对应到底图被覆盖的位置。


## 2.数据流程

创建底图的GraphicsLayer缓存 ->填充底图的GraphicsLayer ->获取底图根布局位置backgroundPosition 

-> 卡片拖动改变 -> 获取卡片根布局位置cardPosition ->卡片启用独立渲染，增加BlurEffect 

-> 计算底图GraphicsLayer的偏移量 -> 填充卡片背景图层 -> 执行高斯模糊特效

## 3.QA

- 1.每次移动，都要做一次高斯模糊运算吗？性能会不会受影响？

	是的，每一帧的拖动，都会导致卡片的位移，那么就会重新计算位置，并渲染新的高斯模糊特效。
  
	但是由于使用的graphicsLayer，会使用GPU的着色器做特效处理，性能开销很小。

- 2.每次高斯模糊是整个底图缓存大小，还是卡片区域大小？
	
	每次高斯的大小是卡片区域。这是由于底图先绘制到到卡片里，然后对卡片做的高斯模糊，高斯模糊的大小自然也是卡片大小。

- 3.使用这种方案，有什么版本限制吗？

	只能在Android12（Android S）上使用，更低版本不支持此高斯模糊的效果。



<img width="540" height="1179" alt="Screenshot_20261109_144937" src="https://github.com/user-attachments/assets/79b9b0c2-330d-4b0d-bbdf-79cd4606f80d" />


