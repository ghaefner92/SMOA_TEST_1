package com.example.imiq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.imiq.ui.theme.IMIQTheme

/** Shared layout rhythm. Use these instead of screen-specific one-off spacing values. */
object ImiqSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val section = 48.dp
}

enum class ImiqCardStyle { Default, Selected, Emphasized, Disabled }

@Composable
fun ImiqCard(
    modifier: Modifier = Modifier,
    style: ImiqCardStyle = ImiqCardStyle.Default,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = when (style) {
        ImiqCardStyle.Selected -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ImiqCardStyle.Emphasized -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ImiqCardStyle.Disabled -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ImiqCardStyle.Default -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    }
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = colors,
        border = if (style == ImiqCardStyle.Selected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)) else null,
    ) {
        Column(Modifier.padding(ImiqSpacing.md), content = content)
    }
}

@Composable
fun PrimaryActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled) {
        if (icon != null) { Icon(icon, contentDescription = null); Spacer(Modifier.width(ImiqSpacing.xs)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AlternativeActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

data class ContextStatusChipSpec(val label: String, val icon: ImageVector, val container: Color, val content: Color)

@Composable
fun contextStatusChipSpec(state: ContextPresentationState, de: Boolean = false): ContextStatusChipSpec {
    val colors = MaterialTheme.colorScheme
    return when (state) {
        ContextPresentationState.AVAILABLE -> ContextStatusChipSpec(state.userLabel(de), Icons.Outlined.CheckCircle, colors.primaryContainer, colors.onPrimaryContainer)
        ContextPresentationState.PARTIAL -> ContextStatusChipSpec(state.userLabel(de), Icons.Outlined.WarningAmber, colors.secondaryContainer, colors.onSecondaryContainer)
        ContextPresentationState.UNKNOWN -> ContextStatusChipSpec(state.userLabel(de), Icons.AutoMirrored.Outlined.HelpOutline, colors.surfaceVariant, colors.onSurfaceVariant)
        ContextPresentationState.NO_DATA -> ContextStatusChipSpec(state.userLabel(de), Icons.Outlined.CloudOff, colors.surfaceVariant, colors.onSurfaceVariant)
        ContextPresentationState.UNAVAILABLE -> ContextStatusChipSpec(state.userLabel(de), Icons.Outlined.CloudOff, colors.errorContainer, colors.onErrorContainer)
        ContextPresentationState.ERROR -> ContextStatusChipSpec(state.userLabel(de), Icons.Outlined.ErrorOutline, colors.errorContainer, colors.onErrorContainer)
    }
}

@Composable
fun ContextStatusChip(state: ContextPresentationState, modifier: Modifier = Modifier, de: Boolean = false) {
    val spec = contextStatusChipSpec(state, de)
    AssistChip(
        onClick = {},
        modifier = modifier.semantics { stateDescription = spec.label },
        enabled = false,
        label = { Text(spec.label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(spec.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = AssistChipDefaults.assistChipColors(disabledContainerColor = spec.container, disabledLabelColor = spec.content, disabledLeadingIconContentColor = spec.content),
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(ImiqSpacing.xxs))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) { Spacer(Modifier.width(ImiqSpacing.sm)); trailing() }
    }
}

enum class InlineErrorSeverity { Error, Warning }

@Composable
fun InlineErrorState(message: String, modifier: Modifier = Modifier, severity: InlineErrorSeverity = InlineErrorSeverity.Error, retryLabel: String? = null, onRetry: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val container = if (severity == InlineErrorSeverity.Error) colors.errorContainer else colors.secondaryContainer
    val content = if (severity == InlineErrorSeverity.Error) colors.onErrorContainer else colors.onSecondaryContainer
    Row(
        modifier.fillMaxWidth().background(container, MaterialTheme.shapes.medium).padding(ImiqSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (severity == InlineErrorSeverity.Error) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber, contentDescription = null, tint = content)
        Spacer(Modifier.width(ImiqSpacing.sm))
        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = content)
        if (onRetry != null && !retryLabel.isNullOrBlank()) {
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(retryLabel, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun InlineLoadingIndicator(label: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ImiqSpacing.sm)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        if (label != null) Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LoadingRouteCard(modifier: Modifier = Modifier) {
    ImiqCard(modifier) {
        InlineLoadingIndicator("Loading route")
        Spacer(Modifier.height(ImiqSpacing.xs))
        Box(Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small))
        Spacer(Modifier.height(ImiqSpacing.xs))
        Box(Modifier.fillMaxWidth(0.65f).height(12.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101416)
@Composable private fun DesignSystemPreview() = IMIQTheme {
    Column(Modifier.padding(ImiqSpacing.md), verticalArrangement = Arrangement.spacedBy(ImiqSpacing.md)) {
        ImiqCard { SectionHeader("Context", subtitle = "Route data") }
        PrimaryActionButton("Plan route", {})
        ContextStatusChip(ContextPresentationState.AVAILABLE)
        InlineErrorState("Context data is temporarily unavailable.", retryLabel = "Retry", onRetry = {})
        LoadingRouteCard()
    }
}
