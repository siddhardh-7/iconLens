package io.github.siddhardh7.iconlens

fun androidResourceReference(resource: IconResource): String = "R.drawable.${resource.name}"
