package com.infosung.atomic.app.version.domain

internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
  override fun toString(): String = "$major.$minor.$patch"
}
