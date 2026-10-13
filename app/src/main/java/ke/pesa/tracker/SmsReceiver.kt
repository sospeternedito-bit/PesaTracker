package ke.pesa.tracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(i)
        if (msgs.isNullOrEmpty()) return
        val from = msgs[0].originatingAddress ?: return
        if (!from.contains("MPESA", ignoreCase = true)) return
        val body = msgs.joinToString("") { it.messageBody ?: "" }

        val p = c.getSharedPreferences("pesa", Context.MODE_PRIVATE)
        val q = JSONArray(p.getString("q", "[]"))
        q.put(JSONObject().put("t", body))
        p.edit().putString("q", q.toString()).apply()

        if (p.getBoolean("popup", false) && Settings.canDrawOverlays(c)) {
            c.startActivity(
                Intent(c, PopupActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("body", body)
            )
            return
        }

        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mpesa", "M-Pesa", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val amt = Regex("Ksh\\s?[\\d,]+(\\.\\d+)?", RegexOption.IGNORE_CASE).find(body)?.value ?: ""
        val n = Notification.Builder(c, "mpesa")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("M-Pesa $amt: tap to classify")
            .setContentText(body.take(100))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), n)
    }
}
