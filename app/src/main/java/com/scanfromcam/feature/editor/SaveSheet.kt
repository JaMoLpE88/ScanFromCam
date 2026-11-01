package com.scanfromcam.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scanfromcam.core.Strings
import com.scanfromcam.core.sanitizeName
import com.scanfromcam.domain.model.DocFormat
import com.scanfromcam.feature.flow.ScanFlowViewModel
import com.scanfromcam.ui.theme.Tokens

@Composable
fun SaveSheet(
    vm: ScanFlowViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val t = Strings.t
    var name by remember { mutableStateOf(sanitizeName("", prefix = t.defaultDocumentPrefix)) }
    val pages = vm.pages.size

    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text(t.saveDocument) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t.name) },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = t.pageCount(pages) + " · " +
                        if (vm.format == DocFormat.PDF) t.pdfWithText else t.imageFormat,
                    color = Tokens.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.save(name) { onSaved() } },
                enabled = !vm.busy && pages > 0,
            ) {
                Text(vm.saveStage ?: if (vm.busy) t.saving else t.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !vm.busy) { Text(t.cancel) }
        },
    )
}

/** Single place to surface recoverable failures of the scan flow. */
@Composable
fun ScanErrorDialog(vm: ScanFlowViewModel) {
    val message = vm.error ?: return
    AlertDialog(
        onDismissRequest = { vm.error = null },
        title = { Text(Strings.t.somethingWrong) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { vm.error = null }) { Text(Strings.t.gotIt) }
        },
    )
}
