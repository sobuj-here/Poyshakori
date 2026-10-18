package com.poyshakori.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DebtReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == ACTION_CHECK_DEBTS) {
            DebtReminderHelper.checkAndNotifyDueDebts(context)
            DebtReminderHelper.checkAndNotifyRecurringBills(context)
            if (action == Intent.ACTION_BOOT_COMPLETED) {
                DebtReminderHelper.scheduleDailyReminder(context)
            }
        }
    }

    companion object {
        const val ACTION_CHECK_DEBTS = "com.poyshakori.app.ACTION_CHECK_DEBTS"
    }
}
