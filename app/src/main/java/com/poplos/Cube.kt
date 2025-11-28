package com.poplos

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Cube {

    private val vertexBuffer: FloatBuffer
    private val drawListBuffer: ByteBuffer
    private val mProgram: Int

    // Standard Cube vertices
    private val cubeCoords = floatArrayOf(
        -0.5f,  0.5f, -0.5f, // Top Left Back
         0.5f,  0.5f, -0.5f, // Top Right Back
         0.5f, -0.5f, -0.5f, // Bottom Right Back
        -0.5f, -0.5f, -0.5f, // Bottom Left Back
        -0.5f,  0.5f,  0.5f, // Top Left Front
         0.5f,  0.5f,  0.5f, // Top Right Front
         0.5f, -0.5f,  0.5f, // Bottom Right Front
        -0.5f, -0.5f,  0.5f  // Bottom Left Front
    )

    // Indices for drawing triangles (12 triangles, 36 vertices)
    private val drawOrder = byteArrayOf(
        0, 1, 2, 0, 2, 3, // Back face
        4, 5, 6, 4, 6, 7, // Front face
        0, 4, 7, 0, 7, 3, // Left face
        1, 5, 6, 1, 6, 2, // Right face
        0, 1, 5, 0, 5, 4, // Top face
        3, 2, 6, 3, 6, 7  // Bottom face
    )

    private val vertexShaderCode =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 vPosition;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * vPosition;" +
        "}"

    private val fragmentShaderCode =
        "precision mediump float;" +
        "uniform vec4 vColor;" +
        "void main() {" +
        "  gl_FragColor = vColor;" +
        "}"

    init {
        val bb = ByteBuffer.allocateDirect(cubeCoords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(cubeCoords)
        vertexBuffer.position(0)

        drawListBuffer = ByteBuffer.allocateDirect(drawOrder.size)
        drawListBuffer.put(drawOrder)
        drawListBuffer.position(0)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(mProgram, vertexShader)
        GLES20.glAttachShader(mProgram, fragmentShader)
        GLES20.glLinkProgram(mProgram)
    }

    fun draw(mvpMatrix: FloatArray, color: FloatArray) {
        GLES20.glUseProgram(mProgram)

        val positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        val colorHandle = GLES20.glGetUniformLocation(mProgram, "vColor")
        GLES20.glUniform4fv(colorHandle, 1, color, 0)

        val vPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_BYTE, drawListBuffer)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
