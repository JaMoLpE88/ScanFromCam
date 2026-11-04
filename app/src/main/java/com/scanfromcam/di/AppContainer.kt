package com.scanfromcam.di

import android.content.Context
import com.scanfromcam.core.AppLock
import com.scanfromcam.core.AppSettings
import com.scanfromcam.core.Updater
import com.scanfromcam.data.LocalDocumentRepository
import com.scanfromcam.data.OcrService
import com.scanfromcam.domain.repository.DocumentRepository

/**
 * Manual dependency container. One instance per process, created by the Application.
 */
class AppContainer(context: Context) {
    val settings = AppSettings(context)
    val appLock = AppLock(settings)
    val repository: DocumentRepository = LocalDocumentRepository(context)
    val ocr: OcrService = OcrService()
    val updater = Updater(context, settings)
}
