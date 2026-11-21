package com.zilu.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MidnightResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ensureToday(context)
        scheduleMidnight(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ensureToday(context)
        scheduleMidnight(context)
    }
}
