package com.samsung.prism.teachable.replay

interface IAppLauncher {
    suspend fun launchApp(packageName: String): Boolean
    suspend fun minimizeToHome(): Boolean
    suspend fun bringSaysoToFront(): Boolean
}
