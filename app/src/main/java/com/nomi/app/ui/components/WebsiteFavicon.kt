package com.nomi.app.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

@Composable
fun WebsiteFavicon(
    sourceUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    contentDescription: String? = null,
) {
    val faviconUrl = remember(sourceUrl) { WebsiteFaviconUrl.build(sourceUrl) }
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
    ) {
        if (faviconUrl == null) {
            FaviconFallback(contentDescription = contentDescription)
        } else {
            SubcomposeAsyncImage(
                model = faviconUrl,
                contentDescription = contentDescription,
                modifier = Modifier.clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = { FaviconFallback(contentDescription = contentDescription) },
                error = { FaviconFallback(contentDescription = contentDescription) },
                success = { SubcomposeAsyncImageContent() },
            )
        }
    }
}

/** Three quiet source orbs. Empty/loading slots shimmer until real site icons arrive. */
@Composable
fun AnimatedWebsiteIconStack(
    sourceUrls: List<String>,
    modifier: Modifier = Modifier,
    maxIcons: Int = 3,
) {
    val boundedMax = maxIcons.coerceIn(1, 3)
    val normalizedSources = remember(sourceUrls, boundedMax) {
        researchSourceIconSlots(sourceUrls, boundedMax)
    }
    val stackWidth = (30 + (18 * (normalizedSources.size - 1))).dp

    Box(
        modifier = modifier.width(stackWidth).height(36.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        normalizedSources.forEachIndexed { index, sourceUrl ->
            WebsiteFavicon(
                sourceUrl = sourceUrl,
                size = 30.dp,
                modifier = Modifier
                    .offset(x = (18 * index).dp)
                    .zIndex(index.toFloat()),
            )
        }
    }
}

internal fun researchSourceIconSlots(sourceUrls: List<String>, maxIcons: Int = 3): List<String?> {
    val boundedMax = maxIcons.coerceIn(1, 3)
    val sources = sourceUrls
        .mapNotNull { sourceUrl ->
            WebsiteFaviconUrl.normalizePublicHttpsHostname(sourceUrl)?.let { sourceUrl }
        }
        .distinctBy(WebsiteFaviconUrl::normalizePublicHttpsHostname)
        .take(boundedMax)
    return sources + List(boundedMax - sources.size) { null }
}

@Composable
private fun FaviconFallback(contentDescription: String?) {
    val transition = rememberInfiniteTransition(label = "source shimmer")
    val shimmer = transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1_450)),
        label = "source shimmer position",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val band = size.width * 0.7f
                val startX = (size.width + band) * shimmer.value - band
                val brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(startX, 0f),
                    end = Offset(startX + band, size.height),
                )
                onDrawBehind { drawRect(brush) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = contentDescription,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
        )
    }
}
