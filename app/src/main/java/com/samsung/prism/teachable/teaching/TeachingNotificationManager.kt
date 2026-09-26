package com.samsung.prism.teachable.teaching

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.samsung.prism.teachable.ui.MainActivity

/**
 * Manages foreground ongoing notifications during the interactive teaching phase.
 * Enables users to see recording status while minimized and quickly Stop & Save or Cancel.
 */
object TeachingNotificationManager {
    private const val TAG = "TeachingNotification"
    const val CHANNEL_ID = "prism_teaching_channel"
    const val NOTIFICATION_ID = 2001

    const val ACTION_STOP_TEACHING = "com.samsung.prism.teachable.ACTION_STOP_TEACHING"
    const val ACTION_CANCEL_TEACHING = "com.samsung.prism.teachable.ACTION_CANCEL_TEACHING"

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SaySo Interactive Learning",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live status and quick controls while SaySo learns your taps"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showTeachingNotification(context: Context, utterance: String, actionCount: Int = 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted; skipping notification")
                return
            }
        }

        try {
            createNotificationChannel(context)
            val notification = buildNotification(context, utterance, actionCount)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show teaching notification: ${e.message}")
        }
    }

    fun updateActionCount(context: Context, utterance: String, actionCount: Int) {
        showTeachingNotification(context, utterance, actionCount)
    }

    fun dismissTeachingNotification(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss teaching notification: ${e.message}")
        }
    }

    private fun buildNotification(context: Context, utterance: String, actionCount: Int): Notification {
        // Tapping the notification body brings MainActivity to the foreground
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Stop & Save action button
        val stopIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_STOP_TEACHING
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val stopPendingIntent = PendingIntent.getActivity(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Cancel action button
        val cancelIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_CANCEL_TEACHING
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val cancelPendingIntent = PendingIntent.getActivity(
            context,
            2,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val subtext = if (actionCount == 0) {
            "SaySo is watching your taps. Perform your flow on your phone."
        } else {
            "$actionCount action(s) recorded • Tap 'Stop & Save' when finished"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🔴 Learning: \"$utterance\"")
            .setContentText(subtext)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_save, "Stop & Save", stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
