package com.example.util

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String, val field: String? = null) : ValidationResult()

    val isSuccess: Boolean get() = this is Success
    val errorMessage: String? get() = (this as? Error)?.message
}

data class ManuscriptValidationInfo(
    val isValid: Boolean,
    val pageCount: Int,
    val fileSize: Long,
    val errorMessage: String? = null
)

object PublicationValidator {

    const val MAX_PDF_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB
    const val MAX_COVER_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB

    fun validateMetadata(
        title: String,
        author: String,
        description: String,
        genre: String,
        language: String,
        price: Double,
        isFree: Boolean,
        previewPages: Int,
        totalPages: Int
    ): ValidationResult {
        if (title.isBlank()) {
            return ValidationResult.Error("Book title is required.", "title")
        }
        if (title.trim().length > 200) {
            return ValidationResult.Error("Book title cannot exceed 200 characters.", "title")
        }
        if (description.isBlank()) {
            return ValidationResult.Error("Synopsis/description is required.", "description")
        }
        if (genre.isBlank()) {
            return ValidationResult.Error("Genre selection is required.", "genre")
        }
        if (language.isBlank()) {
            return ValidationResult.Error("Language is required.", "language")
        }
        if (price < 0.0) {
            return ValidationResult.Error("Price cannot be negative.", "price")
        }
        if (isFree && price > 0.0) {
            return ValidationResult.Error("Free publications must have price set to 0.", "price")
        }
        if (!isFree && price <= 0.0) {
            return ValidationResult.Error("Paid publications must have a price greater than 0.", "price")
        }
        if (previewPages < 1) {
            return ValidationResult.Error("Preview pages must be at least 1.", "previewPages")
        }
        if (totalPages > 0 && previewPages > totalPages) {
            return ValidationResult.Error(
                "Preview pages ($previewPages) cannot exceed total manuscript pages ($totalPages).",
                "previewPages"
            )
        }
        return ValidationResult.Success
    }

    fun validateCover(coverBytes: ByteArray?): ValidationResult {
        if (coverBytes == null || coverBytes.isEmpty()) {
            return ValidationResult.Success // Default cover allowed
        }
        if (coverBytes.size > MAX_COVER_SIZE_BYTES) {
            return ValidationResult.Error("Cover image exceeds 10 MB limit.", "cover")
        }
        return ValidationResult.Success
    }

    fun inspectManuscriptPdf(pdfFile: File?): ManuscriptValidationInfo {
        if (pdfFile == null || !pdfFile.exists()) {
            return ManuscriptValidationInfo(
                isValid = false,
                pageCount = 0,
                fileSize = 0,
                errorMessage = "Manuscript file does not exist."
            )
        }

        val size = pdfFile.length()
        if (size == 0L) {
            return ManuscriptValidationInfo(
                isValid = false,
                pageCount = 0,
                fileSize = 0,
                errorMessage = "Selected PDF file is empty (0 bytes)."
            )
        }

        if (size > MAX_PDF_SIZE_BYTES) {
            return ManuscriptValidationInfo(
                isValid = false,
                pageCount = 0,
                fileSize = size,
                errorMessage = "Manuscript exceeds maximum allowed size of 50 MB."
            )
        }

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pages = renderer.pageCount
            if (pages <= 0) {
                ManuscriptValidationInfo(
                    isValid = false,
                    pageCount = 0,
                    fileSize = size,
                    errorMessage = "The PDF contains no readable pages."
                )
            } else {
                ManuscriptValidationInfo(
                    isValid = true,
                    pageCount = pages,
                    fileSize = size,
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            ManuscriptValidationInfo(
                isValid = false,
                pageCount = 0,
                fileSize = size,
                errorMessage = "Invalid or corrupted PDF file: ${e.message}"
            )
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }
}
