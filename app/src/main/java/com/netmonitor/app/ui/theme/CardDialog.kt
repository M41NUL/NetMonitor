package com.netmonitor.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

object DialogBlur {
    var count by mutableIntStateOf(0)
}

@Composable
fun BlurBehind() {
    DisposableEffect(Unit) {
        DialogBlur.count++
        onDispose { DialogBlur.count-- }
    }
}

@Composable
fun CardDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val cs = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BlurBehind()
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .background(cs.surface, shape)
                .plainCard(shape)
        ) {
            Text(
                title,
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant)
            content()
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    CardDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        },
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
