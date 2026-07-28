package io.github.siddhardh7.iconlens

interface IconRenderer {
    suspend fun render(resource: IconResource): RenderedIcon
}
