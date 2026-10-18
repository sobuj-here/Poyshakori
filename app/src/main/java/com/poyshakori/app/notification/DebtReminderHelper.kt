package com.poyshakori.app.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.poyshakori.app.MainActivity
import com.poyshakori.app.PoyshakoriApp
import com.poyshakori.app.R
import com.poyshakori.app.util.CurrencyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DebtReminderHelper {

    const val CHANNEL_ID = "poyshakori_debt_reminders"
    const val NOTIFICATION_ID_BASE = 50000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_debt_reminders)
            val descriptionText = context.getString(R.string.notification_channel_debt_reminders_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Checks all pending debts and triggers a notification if dueDate == today ("date hole")
     * or dueDate < today ("par hoie gele").
     */
    fun checkAndNotifyDueDebts(context: Context) {
        createNotificationChannel(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PoyshakoriApp.instance.database
                val debts = db.debtDao().getAllDebts().firstOrNull() ?: emptyList()
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                for (debt in debts) {
                    if (debt.status == "PAID") continue
                    if (debt.dueDate.isBlank()) continue

                    val outstanding = debt.amount - debt.paidAmount
                    if (outstanding <= 0) continue

                    val formattedAmount = CurrencyFormatter.formatAmount(context, outstanding)
                    val formattedDueDate = CurrencyFormatter.formatDate(context, debt.dueDate)

                    val isToday = debt.dueDate == today
                    val isOverdue = debt.dueDate < today

                    if (!isToday && !isOverdue) continue

                    val title: String
                    val message: String

                    if (debt.type == "RECEIVABLE") {
                        // Someone borrowed money from user / kew dhar niyeche
                        if (isToday) {
                            title = context.getString(R.string.debt_reminder_due_today_title)
                            message = context.getString(R.string.debt_reminder_receivable_today, debt.personName, formattedAmount)
                        } else {
                            title = context.getString(R.string.debt_reminder_overdue_title)
                            message = context.getString(R.string.debt_reminder_receivable_overdue, debt.personName, formattedAmount, formattedDueDate)
                        }
                    } else {
                        // User borrowed money / ami dhar niyechi
                        if (isToday) {
                            title = context.getString(R.string.debt_reminder_due_today_title)
                            message = context.getString(R.string.debt_reminder_debt_today, debt.personName, formattedAmount)
                        } else {
                            title = context.getString(R.string.debt_reminder_overdue_title)
                            message = context.getString(R.string.debt_reminder_debt_overdue, debt.personName, formattedAmount, formattedDueDate)
                        }
                    }

                    showNotification(context, debt.id.toInt() + NOTIFICATION_ID_BASE, title, message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showNotification(context: Context, notificationId: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "DEBTS")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Missing notification permission on Android 13+
            e.printStackTrace()
        }
    }

    /**
     * Checks all pending recurring bills and scheduled payments.
     */
    fun checkAndNotifyRecurringBills(context: Context) {
        createNotificationChannel(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PoyshakoriApp.instance.database
                val recurringList = db.recurringTransactionDao().getAllActiveRecurring().firstOrNull() ?: emptyList()
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                for (item in recurringList) {
                    if (item.nextDueDate.isBlank()) continue
                    val isToday = item.nextDueDate == today
                    val isOverdue = item.nextDueDate < today

                    if (isToday || isOverdue) {
                        val formattedAmount = CurrencyFormatter.formatAmount(context, item.amount)
                        val title = if (isToday) "Scheduled Payment Due Today" else "Scheduled Payment Overdue"
                        val message = "${item.title} ($formattedAmount) is scheduled for ${CurrencyFormatter.formatDate(context, item.nextDueDate)}."
                        showNotification(context, (item.id + 60000).toInt(), title, message)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Schedules daily alarm to check debts and remind users at 9:00 AM.
     */
    fun scheduleDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DebtReminderReceiver::class.java).apply {
            action = DebtReminderReceiver.ACTION_CHECK_DEBTS
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun scheduleDailyDebtCheck(context: Context) {
        scheduleDailyReminder(context)
    }

    fun cancelDailyDebtCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DebtReminderReceiver::class.java).apply {
            action = DebtReminderReceiver.ACTION_CHECK_DEBTS
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
