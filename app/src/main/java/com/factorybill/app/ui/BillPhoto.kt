package com.factorybill.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.factorybill.app.scan.ScanStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lets the user attach one private photo to an invoice or supplier bill.
 * The image is copied into app-private storage; only the path is stored in the database.
 */
@Composable
fun BillPhotoField(
    path: String?,
    onChange: (String?) -> Unit,
    onMessage: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun save(uri: android.net.Uri, cleanup: File? = null) {
        if (busy) return
        scope.launch {
            busy = true
            val saved = withContext(Dispatchers.IO) { ScanStore.saveAttachment(context, uri) }
            busy = false
            cleanup?.delete()
            if (saved != null) {
                onChange(saved)
                onMessage(null)
            } else {
                onMessage("Could not attach that photo. Try another image.")
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) save(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = cameraUri
        val temp = cameraFile
        cameraUri = null
        cameraFile = null
        if (captured && uri != null) save(uri, cleanup = temp) else temp?.delete()
    }

    SectionCard(
        title = "Bill photo",
        subtitle = if (path == null) "Attach a photo of the paper bill" else "One photo is attached",
    ) {
        if (path == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        try {
                            val (file, uri) = ScanStore.cameraTarget(context)
                            cameraFile = file
                            cameraUri = uri
                            camera.launch(uri)
                        } catch (e: Exception) {
                            onMessage("Could not open the camera: ${e.message}")
                        }
                    },
                    enabled = !busy,
                ) {
                    Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Camera")
                }
                FilledTonalButton(onClick = { gallery.launch("image/*") }, enabled = !busy) {
                    Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery")
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    File(path).name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { openPrivateFile(context, path, "image/*") }) { Text("View") }
                TextButton(
                    onClick = { onChange(null) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove")
                }
            }
        }
        Text(
            if (busy) "Saving photo…" else "The photo is stored privately on this device and is never uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
