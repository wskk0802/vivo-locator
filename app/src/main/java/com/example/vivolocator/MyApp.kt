package com.example.vivolocator

import android.app.Application
import com.tencent.smtt.sdk.QbSdk

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 预加载 X5 内核（第一次会从网络下载）
        QbSdk.initX5Environment(this, object : QbSdk.PreInitCallback {
            override fun onCoreInitFinished() {
                // 内核初始化完成（可能用的系统内核兜底，也可能成功加载 X5）
            }

            override fun onViewInitFinished(isX5: Boolean) {
                // isX5 = true 表示 X5 内核加载成功
                // isX5 = false 表示降级到系统内核（首次下载中会这样）
            }
        })
    }
}
