package io.github.siddhardh7.iconlens

suspend fun loadGallery(source: IconSource, renderer: IconRenderer): List<RenderedIcon> =
    source.discover().map { renderer.render(it) }

fun filterByName(icons: List<RenderedIcon>, query: String): List<RenderedIcon> {
    if (query.isBlank()) return icons
    return icons.filter { it.resource.name.contains(query, ignoreCase = true) }
}
