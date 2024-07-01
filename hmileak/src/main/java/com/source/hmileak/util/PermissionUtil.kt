package com.source.hmileak.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager

object PermissionUtil {

    /** 检测申请回调接口 */
    interface  PermissionListener {
        fun onPermissionGranted(requestCode:Int, permission: Array<out String>, grantResults: Array<Int>)
        fun onPermissionDenied(requestCode:Int,permission:Array<out String>,grantResults:Array<Int>)
    }

    /** 检测权限 */
    fun hasPermission(context: Context, vararg permissions: String): Boolean {
        return permissions.all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
    }


    /** 申请权限 */
    fun  requestPermissions(context: Activity, vararg permissions:String, requestCode:Int
                            , listener:PermissionListener?){
         if(hasPermission(context,*permissions)){
             listener?.onPermissionGranted(requestCode,permissions,arrayOf(PackageManager.PERMISSION_GRANTED))
         }else{
             context.requestPermissions(permissions,requestCode)
         }
    }

}