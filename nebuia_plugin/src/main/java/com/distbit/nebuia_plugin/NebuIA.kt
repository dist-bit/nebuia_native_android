package com.distbit.nebuia_plugin

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.checkSelfPermission
import com.distbit.nebuia_plugin.activities.*
import com.distbit.nebuia_plugin.activities.address.AddressActivity
import com.distbit.nebuia_plugin.activities.face.FaceDetector
import com.distbit.nebuia_plugin.activities.fingerprints.FingersDetector
import com.distbit.nebuia_plugin.activities.id.ScannerID
import com.distbit.nebuia_plugin.activities.sign.Signature
import com.distbit.nebuia_plugin.core.*
import com.distbit.nebuia_plugin.exceptions.CodeException
import com.distbit.nebuia_plugin.exceptions.ReportException
import com.distbit.nebuia_plugin.model.Fingers
import com.distbit.nebuia_plugin.model.Keys
import com.distbit.nebuia_plugin.model.Side
import com.distbit.nebuia_plugin.model.sign.Template
import com.distbit.nebuia_plugin.model.sign.toDocumentFields
import com.distbit.nebuia_plugin.model.ui.Theme
import com.distbit.nebuia_plugin.services.Client
import com.distbit.nebuia_plugin.task.Task
import com.distbit.nebuia_plugin.utils.progresshud.ProgressHUD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File


class NebuIA(private var context: Activity) {

    /**
     * @dev background task execution helper.
     */
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private var svProgressHUD: ProgressHUD
    private val sign = Signature(context)

    /**
     * @dev init NebuIA client with private and public keys.
     */
    init {
        val client = Client()
        context.getString(R.string.nebuia_public_key)
        client.keys = Keys(
            publicKey = context.getString(R.string.nebuia_public_key),
            privateKey = context.getString(R.string.nebuia_secret_key)
        )

        id.Init(context.assets)
        fingers.Init(context.assets)
        face.Init(context.assets)
        task.client = client
        svProgressHUD = ProgressHUD(context)

        // check manifest permissions
        autoRequestAllPermissions()
    }

    /**
     * @dev set custom client server
     */
    fun setClientURI(uri: String) {
        client = uri
    }

    /**
     * @dev setFingerprints score limit
     */
    fun setFingerprintsScore(value: Double) {
        //scoreFingerprints = value
    }

    /**
     * @dev set code password from origin server
     * @param code - 8 digits number code
     */
    fun setTemporalCode(code: String) = task.setOTP(code)

    /**
     * @dev set code password from origin server
     * @param report - user id report
     */
    fun setReport(report: String) = task.setReport(report)

    /**
     * @dev generate new report for user - store this on DB
     * @param onReport - callback function with report
     */
    fun createReport(onReport: (report: String) -> Unit) {
        checkCodeParamRequest()
        uiScope.launch {
            onReport(task.generateReport())
        }
    }

    /**
     * @dev launch face live proof in new activity
     * @param onFaceComplete - this flow has many activities,
     * on end we need to redirect to another local activity
     */
    fun faceLiveDetection(useIDShow: Boolean, onFaceComplete: () -> Unit) {
        checkReportParamRequest()
        faceComplete = onFaceComplete
        val intent = Intent(context, FaceDetector::class.java)
        intent.putExtra(context.getString(R.string.idshow), useIDShow);
        context.startActivity(intent)
    }

    /**
     * @dev launch fingerprint detector
     * @param onFingerDetectionComplete - this flow has many activities,
     * on end we need to redirect to another local activity
     * @return ByteArray - wsq file
     * @return Bitmap - index finger with fingerprint clear image
     */
    fun fingerDetection(
        ptn: Int,
        skip: Boolean,
        onFingerDetectionComplete: (Fingers, Fingers, Fingers, Fingers) -> Unit,
        onSkip: () -> Unit,
        onSkipWithFingers: (Fingers, Fingers, Fingers, Fingers) -> Unit
    ) {
        checkReportParamRequest()
        fingerComplete = onFingerDetectionComplete
        fingerSkipWithImages = onSkipWithFingers
        fingerSkip = onSkip
        skipStep = skip
        positionHand = ptn
        context.startActivity(
            Intent(context, FingersDetector::class.java)
        )
    }

    /**
     * @dev launch fingerprint detector
     * @param onWSQDetectionComplete - return wsq file,
     * on end we need to redirect to another local activity
     * @return ByteArray - wsq file
     */
    fun generateWSQFingerprint(image: Bitmap, onWSQDetectionComplete: (ByteArray?) -> Unit) {
        checkReportParamRequest()
        wsqComplete = onWSQDetectionComplete
        uiScope.launch {
            val wsq = task.getWSQFingerprintImage(image, onError = {
                onWSQDetectionComplete(null)
            })

            if (wsq != null) {
                onWSQDetectionComplete(wsq)
            } else {
                onWSQDetectionComplete(null)
            }
        }
    }

    /**
     * @dev launch record activity
     * @param onRecordComplete - on record process done
     * @return String - path of video
     */
    fun recordActivity(
        text: ArrayList<String>,
        nameFromId: Boolean,
        onRecordComplete: (File) -> Unit
    ) {
        checkReportParamRequest()
        recordComplete = onRecordComplete
        val intent = Intent(context, RecordActivity::class.java)
        intent.putStringArrayListExtra(context.getString(R.string.text_to_load), text)
        intent.putExtra(context.getString(R.string.name_from_id), nameFromId)
        context.startActivity(intent)
    }

    /**
     * @dev launch IDScanner detector
     * @param onIDComplete - this flow has many activities,
     * on end we need to redirect to another local activity
     */
    fun documentDetection(onIDComplete: () -> Unit, onIDError: () -> Unit) {
        checkReportParamRequest()
        idComplete = onIDComplete
        idError = onIDError
        context.startActivity(
            Intent(context, ScannerID::class.java)
        )
    }

    /**
     * @dev launch IDScanner detector
     * @param onCaptureComplete - this flow has many activities,
     * on end we need to redirect to another local activity
     */
    fun genericCapture(onCaptureComplete: (ByteArray?) -> Unit) {
        checkReportParamRequest()
        documentCapture = onCaptureComplete
        context.startActivity(
            Intent(context, CaptureActivity::class.java)
        )
    }

    /**
     * @dev launch Camera activity for  Address proof
     * @param onAddressComplete - return address from image or pdf
     */
    fun captureAddress(onAddressComplete: (HashMap<String, Any>?) -> Unit) {
        checkReportParamRequest()
        addressCapture = onAddressComplete
        context.startActivity(
            Intent(context, AddressActivity::class.java)
        )
        context.overridePendingTransition(0, 0);

    }

    /**
     * @dev save address from string
     * @param onAddressSaveComplete - return on address save
     */
    fun saveAddress(address: String, onAddressSaveComplete: (HashMap<String, Any>) -> Unit) {
        checkReportParamRequest()
        uiScope.launch {
            onAddressSaveComplete(task.setAddress(address))
        }
    }

    /**
     * @dev get face from user
     * @param onFaceImage - callback with bitmap from face user,
     * return null if face not exist
     */
    fun getFaceImage(onFaceImage: (Bitmap?) -> Unit) {
        checkReportParamRequest()
        uiScope.launch {
            onFaceImage(task.getFace())
        }
    }

    /**
     * @dev get document cropped image
     * @param onIDImage - callback with bitmap from document side,
     * @param side - side of document [FRONT/BACK],
     * return null if document not exist
     */
    fun getIDImage(side: Side, onIDImage: (Bitmap?) -> Unit) {
        checkReportParamRequest()
        uiScope.launch {
            onIDImage(task.getIDImage(side))
        }
    }

    /**
     * @dev get report data
     * @param onIDData - callback with bitmap from report,
     * return null if document not exist
     */
    fun getIDData(onIDData: (HashMap<*, *>) -> Unit) {
        checkReportParamRequest()
        uiScope.launch {
            onIDData(task.getReportSummary())
        }
    }

    /**
     * @dev save email address
     */
    fun saveEmail(email: String, onResult: (Boolean) -> Unit) {
        checkReportParamRequest()
        uiScope.launch {
            onResult(task.saveEmail(email))
        }
    }

    /**
     * @dev save phone number
     */
    fun savePhone(phone: String, onResult: (Boolean) -> Unit) {
        checkReportParamRequest()
        uiScope.launch {
            onResult(task.savePhone(phone))
        }
    }

    /**
     * @dev sent OTP code to email address
     */
    fun generateOTPEmail(onResult: (Boolean) -> Unit) {
        checkReportParamRequest()
        uiScope.launch {
            onResult(task.generateOTPEmail())
        }
    }

    /**
     * @dev sent OTP to phone number
     */
    fun generateOTPPhone(onResult: (Boolean) -> Unit) {
        checkReportParamRequest()
        uiScope.launch {
            onResult(task.generateOTPPhone())
        }
    }

    /**
     * @dev save email address
     */
    fun verifyOTPEmail(code: String, onResult: (Boolean) -> Unit) {
        checkReportParamRequest()
        uiScope.launch {
            onResult(task.verifyOTPEmail(code))
        }
    }

    /**
     * @dev save phone address
     */
    fun verifyOTPPhone(code: String, onResult: (Boolean) -> Unit) {
        checkReportParamRequest()
        uiScope.launch {
            onResult(task.verifyOTPPhone(code))
        }
    }

    inner class NebuIASigner {

        /**
         * @dev get documents templates
         */
        fun getSignTemplates(onDocumentTemplates: (List<Template>) -> Unit) {
            svProgressHUD.show()
            uiScope.launch {
                onDocumentTemplates(task.getSignTemplates())
                svProgressHUD.dismiss()
            }
        }

        fun signDocument(
            documentId: String,
            email: String,
            params: MutableMap<String, String>,
            onDocumentSign: (Boolean) -> Unit
        ) {
            checkReportParamRequest()
            documentSigned = onDocumentSign
            uiScope.launch {
                // wait 500 milliseconds to show loading dialog
                delay(500)
                svProgressHUD.show()
                val document = task.signDocument(mutableMapOf(
                    "email" to email,
                    "templateId" to documentId,
                    "fields" to params.toDocumentFields()
                ))
                svProgressHUD.dismiss()
                if (document != null) {
                    sign.openWindow(document)
                }
            }
        }
    }

    private fun autoRequestAllPermissions() {
        var info: PackageInfo? = null
        try {
            info = context.packageManager.getPackageInfo(
                context.applicationContext.packageName,
                PackageManager.GET_PERMISSIONS
            )
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        if (info == null) {
            return
        }
        val permissions: Array<String> = info.requestedPermissions
        var remained = false
        for (permission in permissions) {
            if (checkSelfPermission(context, permission) != PermissionChecker.PERMISSION_DENIED) {
                remained = true
            }
        }
        if (remained) {
            context.requestPermissions(permissions, 0)
        }
    }

    companion object {
        // background tasks
        var task: Task = Task()

        val id: Id = Id()
        val fingers: Finger = Finger()
        val face: Face = Face()

        var idComplete: () -> Unit = {}
        var idError: () -> Unit = {}

        var addressCapture: (HashMap<String, Any>?) -> Unit = { result: HashMap<String, Any>? -> }

        // fingers
        var fingerComplete: (Fingers, Fingers, Fingers, Fingers) -> Unit =
            { index, middle, ring, little: Fingers -> }
        var fingerSkipWithImages: (Fingers, Fingers, Fingers, Fingers) -> Unit =
            { index, middle, ring, little: Fingers -> }
        var fingerSkip: () -> Unit = {}
        var skipStep: Boolean = false

        var positionHand: Int = 0

        // generic document capture
        var documentCapture: (ByteArray) -> Unit = { file: ByteArray -> }

        var wsqComplete: (ByteArray) -> Unit = { file: ByteArray -> }

        // video evidence
        var recordComplete: (File) -> Unit = { file: File -> }

        // face
        var faceComplete: () -> Unit = {}

        // document signed callback
        var documentSigned: (Boolean) -> Unit = { status: Boolean -> }

        // nebuIA theme
        var theme: Theme = Theme()

        // api endpoint
        var client: String = "https://api.nebuia.com/api/v1/services"
    }

    /**
     * @dev check otp code
     */
    private fun checkCodeParamRequest() {
        if (task.client!!.otp.isEmpty()) throw CodeException("not time code set")
    }

    /**
     * @dev check report
     */
    private fun checkReportParamRequest() {
        if (task.client!!.report.isEmpty()) throw ReportException("not report set")
    }
}