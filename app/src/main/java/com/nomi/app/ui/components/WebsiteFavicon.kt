package com.nomi.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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

/** A bounded, gently moving stack suitable for research/progress states. */
@Composable
fun AnimatedWebsiteIconStack(
    sourceUrls: List<String>,
    modifier: Modifier = Modifier,
    maxIcons: Int = 3,
) {
    val boundedMax = maxIcons.coerceIn(1, 3)
    val normalizedSources = remember(sourceUrls, boundedMax) {
        sourceUrls
            .mapNotNull { sourceUrl ->
                WebsiteFaviconUrl.normalizePublicHttpsHostname(sourceUrl)?.let { sourceUrl }
            }
            .distinctBy(WebsiteFaviconUrl::normalizePublicHttpsHostname)
            .take(boundedMax)
    }
    val visibleSources: List<String?> = normalizedSources.ifEmpty { listOf(null) }
    val stackWidth = (30 + (18 * (visibleSources.size - 1))).dp

    Box(
        modifier = modifier.width(stackWidth).height(36.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        visibleSources.forEachIndexed { index, sourceUrl ->
            val transition = rememberInfiniteTransition(label = "research-site-$index")
            val verticalOffset = transition.animateFloat(
                initialValue = -1.5f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 760, delayMillis = index * 120),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "research-site-offset-$index",
            )
            WebsiteFavicon(
                sourceUrl = sourceUrl,
                size = 30.dp,
                modifier = Modifier
                    .offset(x = (18 * index).dp, y = verticalOffset.value.dp)
                    .zIndex(index.toFloat()),
            )
        }
    }
}

@Composable
private fun FaviconFallback(contentDescription: String?) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = contentDescription,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
