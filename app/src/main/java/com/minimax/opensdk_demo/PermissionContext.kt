package com.minimax.opensdk_demo

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Process
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 方便 Activity 和 Fragment 无缝处理权限申请回调的工具类
 */
class PermissionContext : IPermissionContext {

    /**
     * 异步权限申请的监听器
     */
    private var resultListenerAsync: PermissionResultListenerAsync? = null


    /**
     * 权限说明的弹窗
     */
    private var descriptionDialog: Dialog? = null

    /**
     * 检查单个权限
     *
     * @param permission 单个权限
     * @param grantedRunnable 权限赋予的回调
     * @param rejectedRunnable 权限被拒绝的回调 isPermanentRefusal 表示是否被永久性拒绝 true：是 false：否
     */
    override fun checkPermissionAsync(
        requestContext: IRequestPermissionsContext,
        permission: String,
        needShowDescriptionDialog: Boolean,
        rejectedRunnable: ((isPermanentRefusal: Boolean) -> Unit)?,
        grantedRunnable: () -> Unit
    ) {
        checkPermissionAsync(
            requestContext,
            arrayOf(permission),
            needShowDescriptionDialog,
            object : PermissionResultListenerAsync {
                override fun onRequestPermissionsResultAsync(
                    permissions: Array<String>,
                    grantResults: IntArray
                ) {
                    when (grantResults.firstOrNull()) {
                        PERMISSION_GRANTED -> grantedRunnable.invoke()
                        PERMISSION_PERMANENT_REFUSAL -> rejectedRunnable?.invoke(true)
                        else -> rejectedRunnable?.invoke(false)
                    }
                }
            }
        )
    }

    override fun checkPermission(context: Context, permission: String): Int {
        return try {
            context.checkPermission(permission, Process.myPid(), Process.myUid())
        } catch (t: Throwable) {
            t.printStackTrace()
            PackageManager.PERMISSION_DENIED
        }
    }

    /**
     * 检查多个权限
     *
     * @param permissions 多个权限
     * @param resultListenerAsync 多个权限申请结果回调
     */
    override fun checkPermissionAsync(
        requestContext: IRequestPermissionsContext,
        permissions: Array<String>,
        needShowDescriptionDialog: Boolean,
        resultListenerAsync: PermissionResultListenerAsync
    ) {
        val context = requestContext.context() ?: return
        val neededPermissions = getNeededPermissions(context, permissions)
        if (neededPermissions.isEmpty()) { // 全部的权限都已经获取了
            resultListenerAsync.onRequestPermissionsResultAsync(
                permissions,
                IntArray(permissions.size) { PERMISSION_GRANTED }
            )
        } else { // 有部分或者全部的权限还没有获取
            val grantedPermissions = ArrayList(listOf(*permissions))
            grantedPermissions.removeAll(listOf(*neededPermissions).toSet())
            if (grantedPermissions.isNotEmpty()) {
                val grantedSize = grantedPermissions.size
                val grantedArray = IntArray(grantedSize) { PERMISSION_GRANTED }
                val permissionArray = grantedPermissions.toTypedArray()
                resultListenerAsync.onRequestPermissionsResultAsync(
                    permissionArray,
                    grantedArray
                )
            }


            this.resultListenerAsync = resultListenerAsync
            requestContext.requestPermissions(
                neededPermissions,
                getRequestCode(resultListenerAsync)
            )
        }
    }

    /**
     * 获取未授予的权限
     *
     * @param permissions 用户申请的所有权限
     * @return 未授予的权限
     */
    private fun getNeededPermissions(context: Context, permissions: Array<String>): Array<String> {
        val neededPermissions = ArrayList<String>()
        for (permission in permissions) {
            val grantedState = ContextCompat.checkSelfPermission(context, permission)
            if (grantedState != PERMISSION_GRANTED) {
                neededPermissions.add(permission)
            }
        }
        return neededPermissions.toTypedArray()
    }

    /**
     * 获取[resultListenerAsync]内存对象映射的RequestCode
     */
    private fun getRequestCode(listener: Any?): Int {
        return listener.hashCode() and 0x0000ffff
    }

    /**
     * Activity 或 Fragment 的 onRequestPermissionsResult() 中回调时调用的Util方法
     */
    override fun onRequestPermissionsResultAsync(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        activity: Activity
    ) {
        if (requestCode == getRequestCode(resultListenerAsync)) {
            for (index in permissions.indices) {

                if (index < grantResults.size
                    && grantResults[index] == PackageManager.PERMISSION_DENIED
                    && !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        permissions[index]
                    )
                ) {
                    grantResults[index] = PERMISSION_PERMANENT_REFUSAL
                }
            }
            descriptionDialog?.dismiss()
            descriptionDialog = null
            resultListenerAsync?.onRequestPermissionsResultAsync(permissions, grantResults)
        }
    }

    companion object {
        // 用户选择永久性拒绝之后，再次申请无法吊起系统的权限弹窗，申请权限时需要判断是否是永久性拒绝
        const val PERMISSION_PERMANENT_REFUSAL = PackageManager.PERMISSION_DENIED - 1
    }
}

/**
 * 申请权限的上下文
 */
interface IPermissionContext {
    /**
     * 检查多个权限
     *
     * @param permissions 多个权限
     * @param resultListenerAsync 多个权限申请结果回调
     */
    fun checkPermissionAsync(
        requestContext: IRequestPermissionsContext,
        permissions: Array<String>,
        needShowDescriptionDialog: Boolean,
        resultListenerAsync: PermissionResultListenerAsync
    )

    /**
     * 检查单个权限
     *
     * @param permission 单个权限
     * @param grantedRunnable 权限赋予的回调
     * @param rejectedRunnable 权限被拒绝的回调 isPermanentRefusal 表示是否被永久性拒绝 true：是 false：否
     */
    fun checkPermissionAsync(
        requestContext: IRequestPermissionsContext,
        permission: String,
        needShowDescriptionDialog: Boolean,
        rejectedRunnable: ((isPermanentRefusal: Boolean) -> Unit)?,
        grantedRunnable: () -> Unit
    )

    /**
     * 检查单个权限（同步方法）
     */
    fun checkPermission(context: Context, permission: String): Int

    /**
     * Activity 或 Fragment 的 onRequestPermissionsResult() 中回调时调用的Util方法
     */
    fun onRequestPermissionsResultAsync(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        activity: Activity
    )
}

/**
 * 「申请权限的上下文」需要实现的接口
 */
interface IRequestPermissionsContext {
    fun requestPermissions(permissions: Array<String>, requestCode: Int)
    fun context(): Context?
}

/**
 * 多个申请权限的结果回调接口
 */
interface PermissionResultListenerAsync {

    /**
     * 返回申请权限的用户反馈状态
     *
     * @param permissions  需要申请的权限列表
     * @param grantResults 申请权限对应的用户反馈状态 0：允许 -- -1：拒绝 -- -2：永久性拒绝
     */
    fun onRequestPermissionsResultAsync(
        permissions: Array<String>,
        grantResults: IntArray
    )
}