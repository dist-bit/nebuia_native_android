package com.distbit.nebuia_plugin.task

import android.graphics.Bitmap
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.model.*
import com.distbit.nebuia_plugin.core.Finger
import com.distbit.nebuia_plugin.core.Id
import com.distbit.nebuia_plugin.services.Client
import com.distbit.nebuia_plugin.utils.Utils.Companion.toBitMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*


class Task {

    var client: Client? = null
    var currentType: String? = null

    // utils
    var documents: Documents = Documents()
    var fingers: MutableList<Fingers> = mutableListOf()

    // address
    var addressFile: File? = null
    var addressBitmap: Bitmap? = null

    // cropped
    var cropped: Bitmap? = null

    fun setOTP(code: String) {
        client!!.otp = code
    }

    fun setReport(report: String) {
        client!!.report = report
    }

    suspend fun generateReport(): String = client!!.generateReport()
    suspend fun saveEmail(email: String): Boolean = client!!.saveEmail(email)
    suspend fun savePhone(phone: String): Boolean = client!!.savePhone(phone)

    suspend fun generateOTPPhone(): Boolean = client!!.sendOTP(OTP.PHONE)
    suspend fun generateOTPEmail(): Boolean = client!!.sendOTP(OTP.EMAIL)

    suspend fun verifyOTPPhone(code: String): Boolean = client!!.validateOTP(OTP.PHONE, code)
    suspend fun verifyOTPEmail(code: String): Boolean = client!!.validateOTP(OTP.EMAIL, code)

    suspend fun liveDetection(bitmap: Bitmap): Boolean =
        withContext(Dispatchers.Default) {
            return@withContext client!!.saveFace(bitmap)
        }

    suspend fun qualityFace(bitmap: Bitmap): Double = withContext(Dispatchers.Default) {
            return@withContext client!!.qualityFace(bitmap)
        }

    suspend fun documentRealTimeDetection(bitmap: Bitmap): String =
        withContext(Dispatchers.Default) {
            val result: Array<Id.Obj> = NebuIA.id.Detect(bitmap)
            if (result.isNotEmpty()) {
                return@withContext result[0].label
            }
            return@withContext ""
        }

    suspend fun documentCrop(bitmap: Bitmap): Bitmap? =
        withContext(Dispatchers.Default) {
            val result: Array<Id.Obj> = NebuIA.id.Detect(bitmap)

            if (result.isNotEmpty()) {
                val box: Id.Obj = result.first()

                val croppedBmp: Bitmap = Bitmap.createBitmap(
                    bitmap,
                    box.x.toInt(),
                    box.y.toInt(),
                    box.w.toInt(),
                    box.h.toInt()
                )
                currentType = box.label
                setBitmapForIdentity(bitmap)
                return@withContext croppedBmp
            }
            return@withContext null
        }

    suspend fun fingerprintDetection(bitmap: Bitmap): Array<Finger.Obj> =
        withContext(Dispatchers.Default) {
            return@withContext NebuIA.fingers.Detect(bitmap)
        }

    suspend fun fingerprintQuality(bitmap: Bitmap): Float =
        withContext(Dispatchers.Default) {
            return@withContext NebuIA.fingers.Quality(bitmap)
        }

    suspend fun uploadID(onError: () -> Unit): HashMap<String, Any>? = client!!.uploadID(documents, onError)

    suspend fun uploadAddress(onError: () -> Unit): HashMap<String, Any>? =
        withContext(Dispatchers.Default) {
            if(addressBitmap != null)
                return@withContext client!!.getAddress(addressBitmap!!, onError)
            else
                return@withContext client!!.getAddress(addressFile!!, onError)
        }

    suspend fun setAddress(address: String): HashMap<String, Any> =
        withContext(Dispatchers.Default) {
            return@withContext client!!.setAddress(mapOf(
                "address" to listOf(address)
            ))
        }

    suspend fun getReportSummary(): HashMap<*, *> {
        return client!!.geReport()
    }

    suspend fun getFace(): Bitmap? {
        val response = client!!.getFace()
        return if (response.size > 241)
            response.toBitMap()
        else null
    }

    suspend fun getIDImage(side: Side): Bitmap? {
        val response = client!!.geDocumentImage(side)
        return if (response.size > 241)
            response.toBitMap()
        else null
    }

    suspend fun extractFingerprints(image: Bitmap, onError: () -> Unit): HashMap<*, *>? {
        return client!!.detectFingers(image, NebuIA.positionHand, onError)
    }

    suspend fun getWSQFingerprintImage(image: Bitmap, onError: () -> Unit): ByteArray? {
        val response = client!!.getWSQFingerprint(image, onError)
        if(response != null) {
            return if (response.size > 241)
                response
            else null
        }
        return null
    }

    // store document images
    private fun setBitmapForIdentity(image: Bitmap) {
        documents.isPassport = false
        when (currentType) {
            "mx_id_front" -> documents.front = image
            "mx_id_back" -> documents.back = image
            "mx_passport_front" -> {
                documents.front = image
                documents.isPassport = true
            }
        }
    }
}