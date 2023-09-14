package com.distbit.nebuia_plugin.activities.sign

import android.app.Activity
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.distbit.nebuia_plugin.model.sign.DocumentToSign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class Signature(private val act: Activity) {

    private var customTabsSession: CustomTabsSession? = null
    private var document: String = ""

    init {
        CustomTabsClient.bindCustomTabsService(
            this.act,
            act.getString(R.string.com_android_chrome),
            object : CustomTabsServiceConnection() {
                override fun onCustomTabsServiceConnected(
                    componentName: ComponentName,
                    customTabsClient: CustomTabsClient
                ) {
                    customTabsSession = customTabsClient.newSession(object : CustomTabsCallback() {
                        override fun onNavigationEvent(navigationEvent: Int, extras: Bundle?) {
                            super.onNavigationEvent(navigationEvent, extras)
                            if (navigationEvent == TAB_HIDDEN) {
                                checkIfDocumentIsSigned()
                            }
                        }
                    })
                }

                override fun onServiceDisconnected(componentName: ComponentName) {
                    customTabsSession = null
                }
            })
    }

    fun checkIfDocumentIsSigned() {
        val id = this.document
        CoroutineScope(Dispatchers.Main).launch {
            NebuIA.documentSigned(NebuIA.task.isDocumentTemplateSigned(id))
        }
    }

    fun openWindow(document: DocumentToSign) {
        this.document = document.documentId
        val builder = CustomTabsIntent.Builder(customTabsSession)

        builder.setUrlBarHidingEnabled(true)
        builder.setShowTitle(false)
        builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF)

        val customTabsIntent = builder.build()
        customTabsIntent.intent.setPackage(getPackageName())
        customTabsIntent.launchUrl(this.act, Uri.parse(document.signatureLink))
    }

    private fun getPackageName(): String? {
        return CustomTabsClient.getPackageName(
            this.act,
            mutableListOf(act.getString(R.string.com_android_chrome))
        )
    }
}