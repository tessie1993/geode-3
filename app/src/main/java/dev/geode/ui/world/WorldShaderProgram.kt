package dev.geode.ui.world

import android.opengl.GLES30

/** Small fail-fast GLES program; compile errors reach the host's visible fallback. */
internal class WorldShaderProgram(
    vertexSource: String,
    fragmentSource: String,
) {
    val id: Int
    private val uniforms = HashMap<String, Int>()

    init {
        val vertex = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        var fragment = 0
        var program = 0
        try {
            fragment = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
            program = GLES30.glCreateProgram()
            check(program != 0) { "Could not create UI world program." }
            GLES30.glAttachShader(program, vertex)
            GLES30.glAttachShader(program, fragment)
            GLES30.glLinkProgram(program)
            val linked = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)
            check(linked[0] != 0) { "UI world link: ${GLES30.glGetProgramInfoLog(program)}" }
            id = program
        } catch (failure: IllegalStateException) {
            if (program != 0) GLES30.glDeleteProgram(program)
            throw failure
        } finally {
            GLES30.glDeleteShader(vertex)
            if (fragment != 0) GLES30.glDeleteShader(fragment)
        }
    }

    fun uniform(name: String): Int = uniforms.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

    fun release() {
        GLES30.glDeleteProgram(id)
        uniforms.clear()
    }

    private fun compile(
        type: Int,
        source: String,
    ): Int {
        val shader = GLES30.glCreateShader(type)
        check(shader != 0) { "Could not create UI world shader." }
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val message = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("UI world shader: $message")
        }
        return shader
    }
}
