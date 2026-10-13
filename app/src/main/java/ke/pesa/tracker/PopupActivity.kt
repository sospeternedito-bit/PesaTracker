package ke.pesa.tracker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray

class PopupActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val body = intent.getStringExtra("body") ?: ""
        val amt = Regex("Ksh\\s?[\\d,]+(\\.\\d+)?", RegexOption.IGNORE_CASE).find(body)?.value ?: ""
        val low = body.lowercase()
        val choices = when {
            low.contains("you have received") -> listOf("Received")
            low.contains("airtime") -> listOf("Airtime")
            low.contains("withdraw") -> listOf("Withdrawn")
            else -> listOf("Sent", "Paid")
        }

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(56, 48, 56, 32)

        val title = TextView(this)
        title.text = "💰 M-Pesa $amt"
        title.textSize = 20f
        title.setTypeface(title.typeface, Typeface.BOLD)
        box.addView(title)

        val msg = TextView(this)
        msg.text = body.take(180)
        msg.textSize = 14f
        msg.setPadding(0, 20, 0, 28)
        box.addView(msg)

        for (ch in choices) {
            val bt = Button(this)
            bt.text = when (ch) {
                "Sent" -> "Sent to a person"
                "Paid" -> "Paid (Paybill / Till)"
                else -> "Save as $ch"
            }
            bt.setOnClickListener { mark(body, ch); finish() }
            box.addView(bt)
        }
        val open = Button(this)
        open.text = "Open app"
        open.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        box.addView(open)
        setContentView(box)
    }

    private fun mark(body: String, ch: String) {
        val p = getSharedPreferences("pesa", Context.MODE_PRIVATE)
        val q = JSONArray(p.getString("q", "[]"))
        for (i in q.length() - 1 downTo 0) {
            val o = q.optJSONObject(i) ?: continue
            if (o.optString("t") == body) { o.put("ch", ch); break }
        }
        p.edit().putString("q", q.toString()).apply()
    }
}
