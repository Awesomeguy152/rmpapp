package com.nano.min.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Полноэкранный просмотр изображения с поддержкой:
 * - Zoom (pinch-to-zoom и double tap)
 * - Pan (перетаскивание)
 * - Закрытие по нажатию на крестик или swipe down
 */
@Composable
fun FullScreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
    onDownload: ((String) -> Unit)? = null,
    onShare: ((String) -> Unit)? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }
    
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            // Двойной тап - zoom in/out
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                        onTap = {
                            // Одиночный тап - показать/скрыть контролы
                            showControls = !showControls
                        }
                    )
                }
        ) {
            // Изображение с жестами
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // Ограничение масштаба
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            
                            // Перемещение только при увеличении
                            if (scale > 1f) {
                                val maxX = (scale - 1f) * size.width / 2
                                val maxY = (scale - 1f) * size.height / 2
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
            )

            // Верхняя панель с кнопками
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .statusBarsPadding()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Color.White
                        )
                    }

                    Row {
                        if (onShare != null) {
                            IconButton(onClick = { onShare(imageUrl) }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Поделиться",
                                    tint = Color.White
                                )
                            }
                        }
                        if (onDownload != null) {
                            IconButton(onClick = { onDownload(imageUrl) }) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Скачать",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Индикатор масштаба (показывается при zoom)
            if (scale != 1f) {
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * Галерея для просмотра нескольких изображений с пагинацией
 */
@Composable
fun ImageGalleryViewer(
    images: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
    onDownload: ((String) -> Unit)? = null,
    onShare: ((String) -> Unit)? = null
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }
    
    val context = LocalContext.current

    // Сброс zoom при смене изображения
    LaunchedEffect(currentIndex) {
        scale = 1f
        offset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Текущее изображение
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(images[currentIndex])
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                            onTap = {
                                showControls = !showControls
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            
                            if (scale > 1f) {
                                val maxX = (scale - 1f) * size.width / 2
                                val maxY = (scale - 1f) * size.height / 2
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            } else {
                                // Свайп для переключения между изображениями
                                if (pan.x > 50 && currentIndex > 0) {
                                    currentIndex--
                                } else if (pan.x < -50 && currentIndex < images.lastIndex) {
                                    currentIndex++
                                }
                                offset = Offset.Zero
                            }
                        }
                    }
            )

            // Верхняя панель
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .statusBarsPadding()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Color.White
                        )
                    }

                    // Счётчик изображений
                    if (images.size > 1) {
                        Text(
                            text = "${currentIndex + 1} / ${images.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row {
                        if (onShare != null) {
                            IconButton(onClick = { onShare(images[currentIndex]) }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Поделиться",
                                    tint = Color.White
                                )
                            }
                        }
                        if (onDownload != null) {
                            IconButton(onClick = { onDownload(images[currentIndex]) }) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Скачать",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Индикаторы страниц (точки)
            if (images.size > 1) {
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        images.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == currentIndex) 10.dp else 8.dp)
                                    .background(
                                        color = if (index == currentIndex) 
                                            Color.White 
                                        else 
                                            Color.White.copy(alpha = 0.5f),
                                        shape = MaterialTheme.shapes.small
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Полноэкранный просмотр Bitmap изображения (для base64 изображений)
 */
@Composable
fun FullScreenBitmapViewer(
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                        onTap = {
                            showControls = !showControls
                        }
                    )
                }
        ) {
            // Изображение с жестами
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            
                            if (scale > 1f) {
                                val maxX = (scale - 1f) * size.width / 2
                                val maxY = (scale - 1f) * size.height / 2
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
            )

            // Верхняя панель с кнопкой закрытия
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .statusBarsPadding()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Индикатор масштаба
            if (scale != 1f) {
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
