package com.distbit.nebuia_plugin.activities.sign

import android.content.ComponentName
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsServiceConnection
import java.lang.ref.WeakReference


/**
 * Implementation for the CustomTabsServiceConnection that avoids leaking the
 * ServiceConnectionCallback
 */
class ServiceConnection(connectionCallback: ServiceConnectionCallback) :
    CustomTabsServiceConnection() {
    // A weak reference to the ServiceConnectionCallback to avoid leaking it.
    private val mConnectionCallback: WeakReference<ServiceConnectionCallback>

    init {
        mConnectionCallback = WeakReference<ServiceConnectionCallback>(connectionCallback)
    }

    override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
        mConnectionCallback.get()?.onServiceConnected(client)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        mConnectionCallback.get()?.onServiceDisconnected()
    }
}