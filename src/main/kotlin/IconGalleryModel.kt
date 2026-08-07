package io.github.siddhardh7.iconlens

import java.awt.image.BufferedImage

suspend fun loadGallery(source: IconSource, renderer: IconRenderer): List<RenderedIcon> =
    source.discover().map { renderer.render(it) }

fun filterByName(icons: List<RenderedIcon>, query: String): List<RenderedIcon> {
    if (query.isBlank()) return icons
    return icons.filter { it.resource.name.contains(query, ignoreCase = true) }
}

fun rankRenderedIcons(
    icons: List<RenderedIcon.Rendered>,
    query: BufferedImage,
    normalizer: ImageNormalizer,
    engine: SimilarityEngine,
): List<ScoredMatch<RenderedIcon.Rendered>> {
    val queryDescriptor = engine.describe(normalizer.normalize(query))
    return rankBySimilarity(
        engine,
        queryDescriptor,
        icons.map { it to engine.describe(normalizer.normalize(it.image)) },
    )
}
