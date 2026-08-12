package cn.edu.ubaa.api.network

/** 平台日志输出。Android 走 Log.d，JVM 走 println。 */
expect fun platformLog(tag: String, message: String)
