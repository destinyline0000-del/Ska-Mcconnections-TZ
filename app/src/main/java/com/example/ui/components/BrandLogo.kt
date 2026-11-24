package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TanzanianGold
import com.example.ui.theme.TanzanianGoldDeep
import com.example.ui.theme.TanzanianGoldLight
import com.example.ui.theme.TextPrimary

/**
 * Custom Royal Gold Crown + Cinema Film-Reel Crest matching the reference brand mark.
 */
@Composable
fun RoyalCrownMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val goldBrush = Brush.linearGradient(
            colors = listOf(TanzanianGoldLight, TanzanianGold, TanzanianGoldDeep),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        // Small crown perched at top-left/center
        val crownPath = Path().apply {
            moveTo(w * 0.16f, h * 0.30f)
            lineTo(w * 0.12f, h * 0.14f)
            lineTo(w * 0.28f, h * 0.22f)
            lineTo(w * 0.38f, h * 0.08f)
            lineTo(w * 0.48f, h * 0.22f)
            lineTo(w * 0.62f, h * 0.14f)
            lineTo(w * 0.58f, h * 0.30f)
            close()
        }
        drawPath(path = crownPath, brush = goldBrush)

        // Bold dynamic "S" / A-frame cinema pillar on left
        val emblemPath = Path().apply {
            moveTo(w * 0.10f, h * 0.86f)
            lineTo(w * 0.34f, h * 0.32f)
            lineTo(w * 0.48f, h * 0.32f)
            lineTo(w * 0.26f, h * 0.86f)
            close()
        }
        drawPath(path = emblemPath, brush = goldBrush)
        drawPath(
            path = emblemPath,
            color = TanzanianGoldLight,
            style = Stroke(width = w * 0.025f, join = StrokeJoin.Round)
        )

        // Golden swooping film-strip base bar
        drawLine(
            brush = goldBrush,
            start = Offset(w * 0.08f, h * 0.84f),
            end = Offset(w * 0.82f, h * 0.72f),
            strokeWidth = h * 0.08f,
            cap = StrokeCap.Round
        )

        // Cinema Film Reel on right
        val reelCenter = Offset(w * 0.66f, h * 0.48f)
        val reelRadius = w * 0.24f
        drawCircle(
            brush = goldBrush,
            radius = reelRadius,
            center = reelCenter
        )
        drawCircle(
            color = TanzanianGoldLight,
            radius = reelRadius,
            center = reelCenter,
            style = Stroke(width = w * 0.03f)
        )
        // Film reel sprocket holes
        val darkHole = Color(0xFF090A0F)
        val holeR = reelRadius * 0.22f
        val orbit = reelRadius * 0.52f
        drawCircle(color = darkHole, radius = holeR * 0.7f, center = reelCenter)
        drawCircle(color = darkHole, radius = holeR, center = Offset(reelCenter.x, reelCenter.y - orbit))
        drawCircle(color = darkHole, radius = holeR, center = Offset(reelCenter.x, reelCenter.y + orbit))
        drawCircle(color = darkHole, radius = holeR, center = Offset(reelCenter.x - orbit, reelCenter.y))
        drawCircle(color = darkHole, radius = holeR, center = Offset(reelCenter.x + orbit, reelCenter.y))
    }
}

@Composable
fun SkaBrandHeaderTitle(
    subtitle: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        RoyalCrownMark(size = if (compact) 34.dp else 44.dp)
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "SKA ",
                    color = TanzanianGold,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontSize = if (compact) 17.sp else 21.sp,
                    letterSpacing = 0.8.sp,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "MACCONNECTIONS TZ",
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 12.sp else 15.sp,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = subtitle,
                color = TanzanianGoldLight,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 8.5.sp else 10.sp,
                letterSpacing = 1.4.sp,
                maxLines = 1
            )
        }
    }
}
