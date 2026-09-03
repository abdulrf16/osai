package com.osai.voiceassistant.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.osai.voiceassistant.R
import com.osai.voiceassistant.service.ServiceStatus

/**
 * Large, glanceable status dot + label designed to be readable at a glance
 * through a helmet visor: color communicates state without requiring the
 * rider to read fine print while riding.
 */
@Composable
fun StatusIndicator(status: ServiceStatus, modifier: Modifier = Modifier) {
    val (color, labelRes) = when (status) {
        ServiceStatus.STOPPED -> MaterialTheme.colorScheme.outline to R.string.status_idle
        ServiceStatus.LISTENING_WAKE_WORD -> Color(0xFF2E7D32) to R.string.status_listening_wake_word
        ServiceStatus.LISTENING_COMMAND -> Color(0xFFF9A825) to R.string.status_listening_command
        ServiceStatus.PROCESSING -> Color(0xFFF9A825) to R.string.status_processing
        ServiceStatus.SUCCESS -> Color(0xFF2E7D32) to R.string.status_success
        ServiceStatus.ERROR -> Color(0xFFC62828) to R.string.status_error
    }

    val isPulsing = status == ServiceStatus.LISTENING_WAKE_WORD || status == ServiceStatus.LISTENING_COMMAND
    val transition = rememberInfiniteTransition(label = "status-pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPulsing) 0.4f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "status-pulse-alpha"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .size(72.dp)
                .alpha(if (isPulsing) pulseAlpha else 1f)
                .background(color = color, shape = CircleShape)
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
