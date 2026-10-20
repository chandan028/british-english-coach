package com.britishcoach.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.britishcoach.core.model.EvidenceLevel

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val m = modifier.fillMaxWidth()
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = m, colors = colors, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), content = body)
    } else {
        Card(modifier = m, colors = colors, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), content = body)
    }
}

/** A message from the coach, styled as a soft speech bubble. */
@Composable
fun CoachBubble(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text("C", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.width(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f),
        ) {
            Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun LearnerBubble(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(start = 48.dp),
        ) {
            Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** The large record/stop control. The ring grows with input loudness so recording is obvious. */
@Composable
fun RecordButton(recording: Boolean, level: Float, enabled: Boolean, onClick: () -> Unit) {
    val ring by animateFloatAsState(if (recording) 1f + level * 0.25f else 1f, tween(120), label = "ring")
    val bg = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
        if (recording) {
            Box(
                Modifier.size(104.dp).scale(ring).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
            )
        }
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(if (enabled) bg else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { contentDescription = if (recording) "Stop recording" else "Start recording" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = null,
                tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(38.dp),
            )
        }
    }
}

/** Says exactly what evidence a result is based on. */
@Composable
fun EvidenceNote(evidence: EvidenceLevel, fromFallback: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                evidence.label + if (fromFallback) " · built-in feedback" else " · AI coach",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(evidence.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Surface(color = container, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
