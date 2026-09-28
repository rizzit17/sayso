package com.samsung.prism.teachable.replay

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import com.samsung.prism.teachable.ui.MainActivity

class AndroidAppLauncher(private val context: Context) : IAppLauncher {
    private val tag = "AndroidAppLauncher"

    override suspend fun launchApp(packageName: String): Boolean {
        if (packageName.isBlank() || packageName == context.packageName) {
            return false
        }

        return try {
            val intent = when {
                packageName == "com.android.settings" || packageName.endsWith(".settings") -> {
                    Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                }
                packageName == "com.google.android.deskclock" || packageName.contains("deskclock") -> {
                    context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    } ?: Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
                else -> {
                    context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    } ?: if (packageName.contains("setting", ignoreCase = true)) {
                        Intent(Settings.ACTION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                    } else null
                }
            }

            if (intent != null) {
                Log.i(tag, "Launching target app: $packageName with intent $intent")
                context.startActivity(intent)
                true
            } else {
                Log.w(tag, "No launch intent found for package: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch package: $packageName", e)
            false
        }
    }

    override suspend fun minimizeToHome(): Boolean {
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to minimize to home screen", e)
            false
        }
    }

    override suspend fun bringSaysoToFront(): Boolean {
        return try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to bring SaySo to front", e)
            false
        }
    }
}
