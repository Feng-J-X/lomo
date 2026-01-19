package com.lomo.app.feature.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage

@Composable
fun ImageViewerScreen(
    url: String,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {
        ZoomableAsyncImage(
            model = url,
            contentDescription = "Full screen image",
            modifier = Modifier.fillMaxSize(),
            onClick = { onBackClick() } // Tap to dismiss
        )

        // Close button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
}
