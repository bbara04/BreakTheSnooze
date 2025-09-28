package hu.bbara.viewideas.objectdetection.vision

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.objects.DetectedObject
import java.util.Locale

class ObjectGraphic(
    overlay: GraphicOverlay,
    private val detectedObject: DetectedObject
) : GraphicOverlay.Graphic(overlay) {

    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 42f
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        // 1) Transform object bounding box from image space to view space.
        val bbox = detectedObject.boundingBox
        val rect = RectF(
            translateX(bbox.left.toFloat()),
            translateY(bbox.top.toFloat()),
            translateX(bbox.right.toFloat()),
            translateY(bbox.bottom.toFloat())
        )

        // 2) Draw the border (box).
        canvas.drawRect(rect, boxPaint)

        // 3) Draw labels (if any).
        var y = rect.top - 10f
        if (detectedObject.labels.isEmpty()) {
            canvas.drawText("Object", rect.left, y, textPaint)
        } else {
            for (label in detectedObject.labels) {
                val text = "${label.text} (${String.format(Locale.US, "%.2f", label.confidence)})"
                canvas.drawText(text, rect.left, y, textPaint)
                y -= textPaint.textSize + 8f
            }
        }
    }
}
