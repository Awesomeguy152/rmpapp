package com.nano.min.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Утилиты для работы с изображениями
 */
object ImageUtils {

    /**
     * Максимальный размер изображения для аватара (в пикселях)
     */
    private const val MAX_AVATAR_SIZE = 512

    /**
     * Максимальный размер изображения для сообщения (в пикселях)
     */
    private const val MAX_MESSAGE_IMAGE_SIZE = 1920

    /**
     * Качество JPEG сжатия (0-100)
     */
    private const val JPEG_QUALITY = 85

    /**
     * Качество JPEG для аватара
     */
    private const val AVATAR_JPEG_QUALITY = 90

    /**
     * Сжимает изображение для аватара
     * @return ByteArray сжатого изображения
     */
    fun compressForAvatar(context: Context, uri: Uri): ByteArray? {
        return compressImage(context, uri, MAX_AVATAR_SIZE, AVATAR_JPEG_QUALITY)
    }

    /**
     * Сжимает изображение для отправки в сообщении
     * @return ByteArray сжатого изображения
     */
    fun compressForMessage(context: Context, uri: Uri): ByteArray? {
        return compressImage(context, uri, MAX_MESSAGE_IMAGE_SIZE, JPEG_QUALITY)
    }

    /**
     * Основная функция сжатия изображения
     */
    private fun compressImage(
        context: Context,
        uri: Uri,
        maxSize: Int,
        quality: Int
    ): ByteArray? {
        return try {
            // Получаем размеры изображения без загрузки в память
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                return null
            }

            // Вычисляем sample size для уменьшения памяти при загрузке
            val sampleSize = calculateSampleSize(originalWidth, originalHeight, maxSize)

            // Загружаем изображение с уменьшением
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            var bitmap: Bitmap? = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, loadOptions)
            } ?: return null

            // Корректируем ориентацию из EXIF
            bitmap = rotateIfNeeded(context, uri, bitmap!!)

            // Масштабируем до нужного размера
            bitmap = scaleToFit(bitmap, maxSize)

            // Сжимаем в JPEG
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            
            // Освобождаем память
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }

            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Вычисляет sample size для BitmapFactory
     */
    private fun calculateSampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sampleSize = 1
        val maxDimension = max(width, height)
        
        while (maxDimension / sampleSize > maxSize * 2) {
            sampleSize *= 2
        }
        
        return sampleSize
    }

    /**
     * Масштабирует bitmap чтобы вписать в maxSize
     */
    private fun scaleToFit(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDimension = max(width, height)

        if (maxDimension <= maxSize) {
            return bitmap
        }

        val scale = maxSize.toFloat() / maxDimension
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        
        if (scaled != bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        
        return scaled
    }

    /**
     * Поворачивает изображение согласно EXIF данным
     */
    private fun rotateIfNeeded(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            inputStream.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationDegrees == 0f) {
                return bitmap
            }

            val matrix = Matrix().apply {
                postRotate(rotationDegrees)
            }

            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            if (rotated != bitmap && !bitmap.isRecycled) {
                bitmap.recycle()
            }

            rotated
        } catch (e: Exception) {
            bitmap
        }
    }

    /**
     * Получает MIME тип файла
     */
    fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "image/jpeg"
    }

    /**
     * Получает имя файла из URI
     */
    fun getFileName(context: Context, uri: Uri): String {
        var name = "image.jpg"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
