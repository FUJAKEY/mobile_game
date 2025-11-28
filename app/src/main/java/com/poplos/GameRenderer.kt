package com.poplos

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class GameRenderer : GLSurfaceView.Renderer {

    private lateinit var mCube: Cube

    // Matrices
    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    // Camera / Player
    var playerX = 0f
    var playerY = 2f // Height
    var playerZ = 0f

    var cameraYaw = 0f
    var cameraPitch = 0f

    // Physics
    var velocityY = 0f
    private val GRAVITY = -0.01f
    private val JUMP_FORCE = 0.25f
    var onGround = false

    // Input
    var inputMoveX = 0f // Strafe
    var inputMoveZ = 0f // Forward/Back

    // Level
    private val platforms = ArrayList<Platform>()

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.5f, 0.8f, 1.0f, 1.0f) // Sky Blue
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        mCube = Cube()

        // Generate "Only Jump" level
        generateLevel()
    }

    private fun generateLevel() {
        platforms.add(Platform(0f, 0f, 0f, 10f, 1f, 10f, floatArrayOf(0.0f, 0.8f, 0.0f, 1.0f))) // Ground

        // Parkour steps
        var currentY = 0f
        var currentX = 0f
        var currentZ = -10f

        for (i in 1..20) {
            currentY += 1.5f + (Math.random().toFloat() * 1.0f)
            currentX += (Math.random().toFloat() - 0.5f) * 5f
            currentZ -= 3f + (Math.random().toFloat() * 2f)

            val width = 2f + (Math.random().toFloat() * 2f)
            val depth = 2f + (Math.random().toFloat() * 2f)

            // Random Colors
            val r = Math.random().toFloat()
            val g = Math.random().toFloat()
            val b = Math.random().toFloat()

            platforms.add(Platform(currentX, currentY, currentZ, width, 0.5f, depth, floatArrayOf(r, g, b, 1.0f)))
        }
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1f, 100f)
    }

    override fun onDrawFrame(unused: GL10) {
        updatePhysics()

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Camera Logic
        val lookX = playerX + sin(Math.toRadians(cameraYaw.toDouble())).toFloat()
        val lookY = playerY + sin(Math.toRadians(cameraPitch.toDouble())).toFloat()
        val lookZ = playerZ - cos(Math.toRadians(cameraYaw.toDouble())).toFloat()

        Matrix.setLookAtM(viewMatrix, 0, playerX, playerY + 0.5f, playerZ, lookX, lookY, lookZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // Draw Level
        for (platform in platforms) {
            drawPlatform(platform)
        }
    }

    private fun drawPlatform(p: Platform) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, p.x, p.y, p.z)
        Matrix.scaleM(modelMatrix, 0, p.width, p.height, p.depth)

        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, vPMatrix, 0, modelMatrix, 0)

        mCube.draw(mvp, p.color)
    }

    private fun updatePhysics() {
        // Movement relative to camera
        val rad = Math.toRadians(cameraYaw.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()

        // inputMoveZ is Forward/Back (drag Y), inputMoveX is Strafe (drag X)
        // Actually, let's interpret inputs simpler:
        // We need a proper movement vector.
        // For now, let's assume auto-forward if touching? No, let's use the inputs derived from touch.
        // Touch Input is delta, so it acts like velocity.

        val speed = 0.5f // sensitivity
        val dz = -(inputMoveZ * cos - inputMoveX * sin) * speed
        val dx = -(inputMoveZ * sin + inputMoveX * cos) * speed

        // Apply Move (Horizontal)
        // Simple AABB collision check *before* moving would be better, but for "Only Jump" simpler:
        // Move X/Z, then check if we are on a platform.

        playerX += dx
        playerZ += dz

        // Gravity & Vertical
        velocityY += GRAVITY
        playerY += velocityY

        // Ground Collision
        onGround = false

        // Check collision with all platforms
        // Player is a point (or small cylinder) at playerX, playerY, playerZ
        // Platform is AABB centered at p.x, p.y, p.z with size p.width, p.height, p.depth
        // Cube model is -0.5 to 0.5. So scaled: p.x - width/2 ... p.x + width/2

        for (p in platforms) {
            val minX = p.x - p.width/2
            val maxX = p.x + p.width/2
            val minZ = p.z - p.depth/2
            val maxZ = p.z + p.depth/2
            val maxY = p.y + p.height/2

            // Check horizontal bounds
            if (playerX in minX..maxX && playerZ in minZ..maxZ) {
                // Check vertical
                // If we are falling and hit the top
                if (velocityY <= 0 && playerY < (maxY + 1.0f) && playerY > (maxY - 0.5f)) {
                    // Landed
                    playerY = maxY + 1.0f // Stand on top (player height offset ~1.0)
                    velocityY = 0f
                    onGround = true
                }
            }
        }

        // Void kill
        if (playerY < -20f) {
            respawn()
        }
    }

    fun jump() {
        if (onGround) {
            velocityY = JUMP_FORCE
            onGround = false
        }
    }

    private fun respawn() {
        playerX = 0f
        playerY = 2f
        playerZ = 0f
        velocityY = 0f
        cameraYaw = 0f
        cameraPitch = 0f
    }
}

data class Platform(val x: Float, val y: Float, val z: Float, val width: Float, val height: Float, val depth: Float, val color: FloatArray)
