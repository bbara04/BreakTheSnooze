package hu.bbara.viewideas.objectdetection.vision

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.camera.core.CameraSelector

class GraphicOverlay(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {
    private val lock = Any()
    private val graphics: MutableList<Graphic> = ArrayList()
    private var previewWidth = 0
    private var previewHeight = 0
    private var widthScaleFactor = 1.0f
    private var heightScaleFactor = 1.0f
    private var facing = CameraSelector.LENS_FACING_BACK

    abstract class Graphic(private val overlay: GraphicOverlay) {
        protected val context: Context = overlay.context

        abstract fun draw(canvas: Canvas)

        fun scale(x: Float): Float = x * overlay.widthScaleFactor
        fun scaleY(y: Float): Float = y * overlay.heightScaleFactor

        fun translateX(x: Float): Float {
            return if (overlay.facing == CameraSelector.LENS_FACING_FRONT) {
                overlay.width - scale(x)
            } else {
                scale(x)
            }
        }

        fun translateY(y: Float): Float = scaleY(y)

        fun postInvalidate() {
            overlay.postInvalidate()
        }
    }

    fun clear() {
        synchronized(lock) {
            graphics.clear()
        }
        postInvalidate()
    }

    fun add(graphic: Graphic) {
        synchronized(lock) {
            graphics.add(graphic)
        }
    }

    fun setImageSourceInfo(imageWidth: Int, imageHeight: Int, facing: Int) {
        previewWidth = imageWidth
        previewHeight = imageHeight
        this.facing = facing
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(lock) {
            if (previewWidth > 0 && previewHeight > 0) {
                widthScaleFactor = width.toFloat() / previewWidth
                heightScaleFactor = height.toFloat() / previewHeight
            }
            for (graphic in graphics) {
                graphic.draw(canvas)
            }
        }
    }
}
