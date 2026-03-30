package com.example.chainreact

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

class BoardView(context: Context, private val engine: GameEngine, private val onCellClicked: (Int, Int) -> Unit) : View(context) {

    private val linePaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val p1Paint = Paint().apply { color = Color.parseColor("#FF5252") }
    private val p2Paint = Paint().apply { color = Color.parseColor("#448AFF") }

    // NEW: Track the current rotation of the molecules
    private var rotationAngle = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellWidth = width.toFloat() / engine.cols
        val cellHeight = height.toFloat() / engine.rows

        // Draw Grid
        for (i in 0..engine.cols) {
            canvas.drawLine(i * cellWidth, 0f, i * cellWidth, height.toFloat(), linePaint)
        }
        for (j in 0..engine.rows) {
            canvas.drawLine(0f, j * cellHeight, width.toFloat(), j * cellHeight, linePaint)
        }

        // Draw Atoms/Mass
        val radius = Math.min(cellWidth, cellHeight) / 5.5f // Slightly smaller to fit rotations
        val orbitRadius = radius * 1.3f
        var hasMoleculesToAnimate = false

        for (i in 0 until engine.cols) {
            for (j in 0 until engine.rows) {
                val cell = engine.grid[i][j]
                if (cell.mass > 0) {
                    val paint = if (cell.owner == 1) p1Paint else p2Paint
                    val cx = i * cellWidth + cellWidth / 2
                    val cy = j * cellHeight + cellHeight / 2
                    
                    if (cell.mass == 1) {
                        // 1 atom: stays perfectly still in the center
                        canvas.drawCircle(cx, cy, radius, paint)
                    } else {
                        // 2 or 3 atoms: Spin them around the center!
                        hasMoleculesToAnimate = true
                        canvas.save()
                        canvas.translate(cx, cy)
                        canvas.rotate(rotationAngle)
                        
                        when (cell.mass) {
                            2 -> {
                                canvas.drawCircle(-orbitRadius, 0f, radius, paint)
                                canvas.drawCircle(orbitRadius, 0f, radius, paint)
                            }
                            3 -> {
                                // Space them perfectly 120 degrees apart
                                for(k in 0..2) {
                                    val angle = Math.toRadians((k * 120).toDouble())
                                    val x = (orbitRadius * Math.cos(angle)).toFloat()
                                    val y = (orbitRadius * Math.sin(angle)).toFloat()
                                    canvas.drawCircle(x, y, radius, paint)
                                }
                            }
                        }
                        canvas.restore() // Reset canvas for the next cell
                    }
                }
            }
        }

        // NEW: If there are grouped molecules, keep advancing the angle and redrawing
        if (hasMoleculesToAnimate) {
            rotationAngle += 4f // Adjust this number to change rotation speed
            if (rotationAngle >= 360f) rotationAngle -= 360f
            invalidate() // Creates a continuous animation loop
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val cellWidth = width / engine.cols
            val cellHeight = height / engine.rows
            val x = (event.x / cellWidth).toInt().coerceIn(0, engine.cols - 1)
            val y = (event.y / cellHeight).toInt().coerceIn(0, engine.rows - 1)
            onCellClicked(x, y)
            return true
        }
        return super.onTouchEvent(event)
    }
}
