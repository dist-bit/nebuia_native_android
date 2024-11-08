package com.distbit.nebuia_plugin

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.checkSelfPermission
import com.distbit.nebuia_plugin.activities.*
import com.distbit.nebuia_plugin.core.*
import com.distbit.nebuia_plugin.model.ui.Theme
import com.distbit.nebuia_plugin.task.Task
import com.distbit.nebuia_plugin.utils.progresshud.ProgressHUD
import java.io.File


class NebuIA(private var context: Activity) {

    /**
     * @dev background task execution helper.
     */
    private var svProgressHUD: ProgressHUD

    /**
     * @dev init NebuIA client with private and public keys.
     */
    init {

        face.Init(context.assets)
        svProgressHUD = ProgressHUD(context)

        // check manifest permissions
        autoRequestAllPermissions()
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
        recordComplete = onRecordComplete
        val intent = Intent(context, RecordActivity::class.java)
        intent.putStringArrayListExtra(context.getString(R.string.text_to_load), text)
        intent.putExtra(context.getString(R.string.name_from_id), nameFromId)
        context.startActivity(intent)
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


        val face: Face = Face()
        var recordComplete: (File) -> Unit = { file: File -> }
        var theme: Theme = Theme()

    }
}