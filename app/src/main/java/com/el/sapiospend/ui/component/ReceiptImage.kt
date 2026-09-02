package com.el.sapiospend.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.el.sapiospend.receipt.ReceiptStore
import com.el.sapiospend.ui.theme.AppColors

/**
 * A stored receipt, decoded off the main thread.
 *
 * Hand-rolled rather than pulled in with an image loader: the app shows at most one
 * image at a time, from local storage, at a known size. A caching loader earns its
 * dependency on a scrolling grid of remote images, and there isn't one here.
 *
 * A missing file draws the placeholder rather than nothing, because the file *can* go —
 * a restored backup carries the database row and not the photo — and an empty box would
 * read as a bug rather than as a receipt that is no longer there.
 */
@Composable
fun ReceiptImage(
    path: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Receipt",
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val store = remember(context) { ReceiptStore(context) }
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        val bitmap = store.load(path)
        image = bitmap?.asImageBitmap()
        failed = bitmap == null
    }

    Box(
        modifier.background(AppColors.BG, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } ?: if (failed) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = "Receipt image is missing",
                tint = AppColors.Border,
                modifier = Modifier.size(24.dp)
            )
        } else Unit
    }
}
