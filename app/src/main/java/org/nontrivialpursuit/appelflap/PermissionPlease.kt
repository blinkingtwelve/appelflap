package org.nontrivialpursuit.appelflap

import android.R.color
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


const val PERMISSIONS_INTENT_EXTRA_FIELD = "permissions_request"

fun havePermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

fun askForPermissions(
        context: Context, desired_permissions: List<String>): Boolean {
    return desired_permissions.filter {
        // filter out permissions we already have, or that are granted without user intervention
        !havePermission(context, it) && runCatching {
            (context.packageManager.getPermissionInfo(
                it, 0
            ).protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS
        }.getOrNull() ?: false
    }.takeIf { it.isNotEmpty() }?.let {
        launchPermissionRequest(context, it)
        true
    } ?: false
}

fun launchPermissionRequest(
        context: Context, permissions: List<String>) {
    Intent().apply {
        setClass(context, PermissionPlease::class.java)
        flags = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NEW_TASK
        putExtra(PERMISSIONS_INTENT_EXTRA_FIELD, permissions.toTypedArray())
    }.also { context.startActivity(it) }
}


class PermissionPlease : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFullScreenFlags()
        window.setBackgroundDrawableResource(color.black)
        intent.extras?.getStringArray(PERMISSIONS_INTENT_EXTRA_FIELD)?.also {
            ActivityCompat.requestPermissions(this, it, 1)
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }
}