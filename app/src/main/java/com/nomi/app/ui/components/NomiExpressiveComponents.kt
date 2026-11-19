package com.nomi.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.nomi.app.ui.localization.nomiString

enum class ExpressiveBadgeStyle {
    COOKIE_9,
    COOKIE_4,
    CLOVER_4,
    FLOWER,
    SOFT_BURST,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ExpressiveBadgeStyle.toPolygon(): RoundedPolygon = when (this) {
    ExpressiveBadgeStyle.COOKIE_9 -> MaterialShapes.Cookie9Sided
    ExpressiveBadgeStyle.COOKIE_4 -> MaterialShapes.Cookie4Sided
    ExpressiveBadgeStyle.CLOVER_4 -> MaterialShapes.Clover4Leaf
    ExpressiveBadgeStyle.FLOWER -> MaterialShapes.Flower
    ExpressiveBadgeStyle.SOFT_BURST -> MaterialShapes.SoftBurst
}

/**
 * Unified Material 3 Expressive tonal icon badge shared across Progress, Library, Today, Goals,
 * and Settings sub-pages so the whole app speaks the same visual language.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NomiExpressiveBadge(
    icon: ImageVector,
    style: ExpressiveBadgeStyle,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    padding: Dp = 10.dp,
    iconSize: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = style.toPolygon().toShape(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(modifier = Modifier.padding(padding), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** Consistent tonal back button used across all Nomi sub-pages. */
@Composable
fun NomiTonalBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription: String = nomiString("Back"),
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.padding(start = 8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

/**
 * Grouped tonal tile corner geometry: large outer corners (`26.dp`) on the first/last item of a
 * section and snug inner corners (`6.dp`) where tiles meet.
 */
fun expressiveGroupedTileShape(index: Int, count: Int): Shape {
    val outer = 26.dp
    val inner = 6.dp
    val first = index == 0
    val last = index == count - 1
    return RoundedCornerShape(
        topStart = if (first) outer else inner,
        topEnd = if (first) outer else inner,
        bottomStart = if (last) outer else inner,
        bottomEnd = if (last) outer else inner,
    )
}
