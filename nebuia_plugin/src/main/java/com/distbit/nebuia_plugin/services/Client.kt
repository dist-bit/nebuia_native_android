package com.distbit.nebuia_plugin.services

import android.graphics.Bitmap
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.model.*
import com.distbit.nebuia_plugin.utils.Utils.Companion.getJsonFromMap
import com.distbit.nebuia_plugin.utils.Utils.Companion.toArray
import com.distbit.nebuia_plugin.utils.Utils.Companion.toMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.create
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.lang.Exception
import java.util.concurrent.TimeUnit


class Client {
    var keys: Keys? = null
    var report: String = ""
    var otp: String = ""
    private val signaturePath: String = "https://api.distbit.io/contracts-api"

    private var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(70, TimeUnit.SECONDS)
        .writeTimeout(70, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS)
        .build()

    private fun JSONObject.jsonMediaType(): RequestBody {
        return this.toString().toRequestBody(
            "application/json; charset=utf-8".toMediaType()
        )
    }

    // build request
    private fun build(path: String, isSign: Boolean = false): Request.Builder {
        val basePath = if (isSign) signaturePath else NebuIA.client
        val url = if (isSign) "$basePath/$path" else "$basePath/$path?report=$report"
        val headers = buildHeaders()

        return Request.Builder()
            .url(url)
            .headers(headers)
    }

    private fun buildHeaders(): Headers {
        val publicKey = keys?.publicKey ?: ""
        val privateKey = keys?.privateKey ?: ""

        return Headers.Builder()
            .add("api_key", publicKey)
            .add("api_secret", privateKey)
            .add("time_key", otp)
            .build()
    }

    // convert response string to json
    private val Response.json
        get() = JSONObject(this.body!!.string())

    // generate new report
    suspend fun generateReport(): String = withContext(Dispatchers.IO) {
        val body = create(null, ByteArray(0))

        val response: Response = client.newCall(
            build("report")
                .post(body)
                .build()
        ).execute()

        report = response.json.getString("payload")
        return@withContext report
    }

    // generate WSQ for fingerprint
    suspend fun getWSQFingerprint(file: Bitmap, onError: () -> Unit): ByteArray? =
        withContext(Dispatchers.IO) {
            val image: RequestBody = create("image/jpeg".toMediaType(), file.toArray())

            val body: RequestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("front", "temp.jpeg", image)
                .build()

            try {
                val response: Response = client.newCall(
                    build("wsq")
                        .post(body)
                        .build()
                ).execute()

                return@withContext response.body!!.bytes()
            } catch (e: Exception) {
                onError()
                return@withContext null
            }
        }

    // generate WSQ for fingerprint
    suspend fun getNFIQFingerprint(file: Bitmap, onError: () -> Unit): HashMap<String, Any>? =
        withContext(Dispatchers.IO) {
            val image: RequestBody = create("image/jpeg".toMediaType(), file.toArray())

            val body: RequestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "temp.jpeg", image)
                .build()

            try {
                val response: Response = client.newCall(
                    build("fingerprints/nfiq")
                        .post(body)
                        .build()
                ).execute()

                return@withContext toMap(response.json)
            } catch (e: Exception) {
                onError()
                return@withContext null
            }
        }

    // store image face
    suspend fun saveFace(file: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val image: RequestBody = create("image/jpeg".toMediaType(), file.toArray())

        val body: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("face", "temp.jpeg", image)
            .build()

        val response: Response = client.newCall(
            build("face")
                .post(body)
                .build()
        ).execute()

        if (response.isSuccessful) {
            val json = response.json
            if (json.getBoolean("status")) {
                return@withContext json.getJSONObject("payload")["status"] as Boolean
            }
            return@withContext false
        }

        return@withContext false
    }

    // store image face
    suspend fun qualityFace(file: Bitmap): Double = withContext(Dispatchers.IO) {
        val image: RequestBody = create("image/jpeg".toMediaType(), file.toArray())

        val body: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("face", "temp.jpeg", image)
            .build()

        val response: Response = client.newCall(
            build("face/quality")
                .post(body)
                .build()
        ).execute()

        if (response.isSuccessful) {
            val json = response.json
            if (json.getBoolean("status")) {
                return@withContext json.getDouble("payload")
            }
            return@withContext 0.0
        }

        return@withContext 0.0
    }

    // uploadID
    suspend fun uploadID(onError: () -> Unit): HashMap<String, Any>? = withContext(Dispatchers.IO) {
        val docs = Documents
        val front: RequestBody = create("image/jpeg".toMediaType(), docs.frontImage()!!.toArray())
        val isPassport = docs.type() == DocumentType.PASSPORT

        val body: MultipartBody.Builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("document", if (!isPassport) "id" else "passport")
            .addFormDataPart("front", "front.jpeg", front)

        if (!isPassport) {
            val back: RequestBody = create("image/jpeg".toMediaType(), docs.backImage()!!.toArray())
            body.addFormDataPart("back", "back.jpeg", back)

        }

        try {
            val response: Response = client.newCall(
                build("id/cropped/experimental")
                    .post(body.build())
                    .build()
            ).execute()

            return@withContext toMap(response.json)
        } catch (e: Exception) {
            onError()
            return@withContext null
        }
    }

    // extract address from image
    suspend fun getAddress(file: Bitmap, onError: () -> Unit): HashMap<String, Any>? =
        withContext(Dispatchers.IO) {
            val front: RequestBody = create("image/jpeg".toMediaType(), file.toArray())

            val body: MultipartBody.Builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("document", "doc.jpeg", front)

            try {
                val response: Response = client.newCall(
                    build("address")
                        .post(body.build())
                        .build()
                ).execute()

                return@withContext toMap(response.json)
            } catch (e: Exception) {
                onError()
                return@withContext null
            }
        }

    // extract address from file
    suspend fun getAddress(file: File, onError: () -> Unit): HashMap<String, Any>? =
        withContext(Dispatchers.IO) {
            val front: RequestBody = create("application/pdf".toMediaType(), file)

            val body: MultipartBody.Builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("document", "doc.pdf", front)

            try {
                val response: Response = client.newCall(
                    build("address")
                        .post(body.build())
                        .build()
                ).execute()

                return@withContext toMap(response.json)
            } catch (e: Exception) {
                onError()
                return@withContext null
            }
        }

    // set address to report
    suspend fun setAddress(data: Map<String, List<String>>): HashMap<String, Any> = withContext(
        Dispatchers.IO
    ) {
        val json = JSONObject(data)
        val body = json.jsonMediaType()

        val response: Response = client.newCall(
            build("address")
                .put(body)
                .build()
        ).execute()

        return@withContext toMap(response.json)
    }

    // get complete report
    suspend fun geReport(): HashMap<String, Any> = withContext(Dispatchers.IO) {
        val response: Response = client.newCall(
            build("report")
                .get()
                .build()
        ).execute()
        return@withContext toMap(response.json)
    }

    // get face image
    suspend fun getFace(): ByteArray = withContext(Dispatchers.IO) {
        val response: Response = client.newCall(
            build("faces")
                .get()
                .build()
        ).execute()
        return@withContext response.body!!.bytes()
    }

    // get document image
    suspend fun geDocumentImage(side: Side): ByteArray = withContext(Dispatchers.IO) {
        val sideDocument = if (side == Side.FRONT) {
            "front"
        } else {
            "back"
        }
        val response: Response = client.newCall(
            build("docs/${sideDocument}")
                .get()
                .build()
        ).execute()

        return@withContext response.body!!.bytes()
    }

    // get address image
    suspend fun geDocumentAddress(): ByteArray = withContext(Dispatchers.IO) {
        val response: Response = client.newCall(
            build("address")
                .get()
                .build()
        ).execute()

        return@withContext response.body!!.bytes()
    }

    // save email
    suspend fun saveEmail(email: String): Boolean = withContext(Dispatchers.IO) {
        val json = getJsonFromMap(
            mapOf(
                "email" to email
            )
        )

        val body = json.jsonMediaType()

        val response: Response = client.newCall(
            build("email")
                .put(body)
                .build()
        ).execute()

        if (response.isSuccessful)
            return@withContext response.json.getBoolean("status")

        return@withContext false
    }

    // save phone
    suspend fun savePhone(phone: String): Boolean = withContext(Dispatchers.IO) {
        val json = getJsonFromMap(
            mapOf(
                "phone" to phone
            )
        )

        val body = json.jsonMediaType()

        val response: Response = client.newCall(
            build("phone")
                .put(body)
                .build()
        ).execute()

        if (response.isSuccessful)
            return@withContext response.json.getBoolean("status")

        return@withContext false
    }

    // send otp code to email/sms
    suspend fun sendOTP(item: OTP): Boolean = withContext(Dispatchers.IO) {
        val response: Response = client.newCall(
            build("otp/generate/${item.item}")
                .get()
                .build()
        ).execute()

        if (response.isSuccessful)
            return@withContext response.json.getBoolean("status")

        return@withContext false
    }

    // validate otp code from email/sms
    suspend fun validateOTP(item: OTP, code: String): Boolean = withContext(Dispatchers.IO) {
        val response: Response = client.newCall(
            build("otp/validate/${item.item}/$code")
                .get()
                .build()
        ).execute()

        if (response.isSuccessful)
            return@withContext response.json.getBoolean("status")

        return@withContext false
    }

    // get documents templates
    suspend fun getSignTemplates(): HashMap<*, *> = withContext(Dispatchers.IO) {
        val response: Response = client.newCall(
            build("advanced-signature-templates/by/company", isSign = true)
                .get()
                .build()
        ).execute()
        return@withContext toMap(response.json)
    }

    suspend fun signDocument(data: MutableMap<String, Any>): HashMap<*, *> =
        withContext(Dispatchers.IO) {
            data["kycId"] = report // append report user
            val json = getJsonFromMap(data)
            val body = json.jsonMediaType()

            val response: Response = client.newCall(
                build("advanced-signature/template/by/user", isSign = true)
                    .post(body)
                    .build()
            ).execute()
            return@withContext toMap(response.json)
        }

    suspend fun checkDocumentSign(documentId: String): Boolean = withContext(Dispatchers.IO) {
        val response: Response = client.newCall(
            build(
                "advanced-signature/verify/document/signed/${documentId}/${report}",
                isSign = true
            )
                .get()
                .build()
        ).execute()

        val json = response.json
        val status = json["status"] as? Boolean
        val payload = json["payload"]
        if (!status!!) {
            return@withContext false
        }

        if (payload is String) {
            return@withContext false
        }

        return@withContext (json["payload"] as Boolean)
    }
}