package ke.pesa.tracker

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PopupActivity : Activity() {
    private val outCats = listOf("Food", "Rent", "Transport", "Airtime & data", "Bills", "School fees", "Health", "Savings / Investing", "Fun", "Debt repayment", "Other")
    private val inCats = listOf("Salary", "Business", "Gift", "Investment returns", "Other income")
    private val blue = Color.parseColor("#2563EB")
    private var ch = "Sent"
    private var cat = "Other"
    private lateinit var seg: LinearLayout
    private lateinit var chips: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
    private fun label(t: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
        return v
    }

    private fun guess(low: String, incoming: Boolean): String = when {
        incoming -> if (low.contains("salary") || low.contains("payroll")) "Salary" else "Other income"
        low.contains("airtime") -> "Airtime & data"
        Regex("kplc|kenya power|water|zuku|dstv|gotv|startimes|wifi|internet").containsMatchIn(low) -> "Bills"
        Regex("naivas|quickmart|carrefour|chandarana|supermarket|restaurant|cafe|kfc|java|pizza|butcher|hotel|kibanda|mama mboga|food").containsMatchIn(low) -> "Food"
        Regex("uber|bolt|little|matatu|fuel|petrol|shell|rubis|parking").containsMatchIn(low) -> "Transport"
        Regex("school|college|university|fees").containsMatchIn(low) -> "School fees"
        Regex("pharmacy|hospital|clinic|chemist").containsMatchIn(low) -> "Health"
        Regex("rent|landlord|apartments").containsMatchIn(low) -> "Rent"
        else -> "Other"
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val body = intent.getStringExtra("body") ?: ""
        val low = body.lowercase()
        val amt = Regex("Ksh\\s?([\\d,]+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val incoming = low.contains("you have received")
        ch = when {
            incoming -> "Received"
            low.contains("for account") || low.contains("paid to") || low.contains("airtime") -> "Paid"
            else -> "Sent"
        }
        cat = guess(low, incoming)

        if (!incoming) {
            val plan = takePlan(amt)
            if (plan != null) {
                ch = plan.optString("ch", "Paid")
                cat = plan.optString("cat", "Other")
                mark(body, "out", amt, plan.optString("id"))
                budgetCheck(cat, amt)
                Toast.makeText(this, "Saved to " + cat + " ✓", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }

        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val cardBg = if (dark) Color.parseColor("#131C2E") else Color.WHITE
        val ink = if (dark) Color.parseColor("#E6EDF8") else Color.parseColor("#14213D")
        val muted = if (dark) Color.parseColor("#8A9BB8") else Color.parseColor("#5F6F8C")
        val soft = if (dark) Color.parseColor("#1A2842") else Color.parseColor("#E7EFFD")

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#99000000"))
        root.setOnClickListener { finish() }

        val sheet = LinearLayout(this)
        sheet.orientation = LinearLayout.VERTICAL
        sheet.background = shape(cardBg, 28)
        sheet.elevation = dp(10).toFloat()
        sheet.isClickable = true
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        lp.setMargins(dp(12), dp(12), dp(12), dp(16))
        root.addView(sheet, lp)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.VERTICAL
        head.setPadding(dp(22), dp(20), dp(22), dp(20))
        val r = dp(28).toFloat()
        head.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#2563EB"), Color.parseColor("#1E3A8A")))
            .apply { cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
        head.addView(label("💰  NEW M-PESA MESSAGE", 11f, Color.parseColor("#CFE0FF"), true))
        val amtText = String.format(Locale.US, "KES %,.2f", amt).replace(".00", "")
        val big = label(amtText, 32f, Color.WHITE, true)
        big.setPadding(0, dp(6), 0, dp(4))
        head.addView(big)
        val sm = label(body.take(110), 13f, Color.parseColor("#DCE8FF"))
        sm.maxLines = 2
        head.addView(sm)
        sheet.addView(head)

        val pad = LinearLayout(this)
        pad.orientation = LinearLayout.VERTICAL
        pad.setPadding(dp(18), dp(18), dp(18), dp(16))
        sheet.addView(pad)

        pad.addView(label("What was this?", 12f, muted, true))
        seg = LinearLayout(this)
        seg.orientation = LinearLayout.HORIZONTAL
        seg.background = shape(soft, 16)
        seg.setPadding(dp(4), dp(4), dp(4), dp(4))
        val segLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        segLp.topMargin = dp(8)
        pad.addView(seg, segLp)

        val catLabel = label("Category", 12f, muted, true)
        catLabel.setPadding(0, dp(16), 0, dp(8))
        pad.addView(catLabel)
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        hs.addView(chips)
        pad.addView(hs)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val later = label("Later", 15f, muted, true)
        later.gravity = Gravity.CENTER
        later.setPadding(0, dp(14), 0, dp(14))
        later.setOnClickListener { finish() }
        row.addView(later, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val save = label("Save  ✓", 16f, Color.WHITE, true)
        save.gravity = Gravity.CENTER
        save.setPadding(0, dp(14), 0, dp(14))
        save.background = shape(blue, 16)
        save.setOnClickListener {
            val kind = if (ch == "Received") "in" else "out"
            mark(body, kind, amt)
            if (kind == "out") budgetCheck(cat, amt)
            finish()
        }
        row.addView(save, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        val rowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rowLp.topMargin = dp(20)
        pad.addView(row, rowLp)

        fun renderSeg() {
            seg.removeAllViews()
            for (c in listOf("Received", "Sent", "Paid")) {
                val on = c == ch
                val t = label(c, 14f, if (on) Color.WHITE else ink, true)
                t.gravity = Gravity.CENTER
                t.setPadding(0, dp(12), 0, dp(12))
                if (on) t.background = shape(blue, 12)
                t.setOnClickListener {
                    val wasIn = ch == "Received"
                    ch = c
                    val isIn = ch == "Received"
                    if (wasIn != isIn) cat = if (isIn) "Other income" else guess(low, false)
                    renderSeg()
                    renderChips(ink, soft)
                }
                seg.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        renderSeg()
        renderChips(ink, soft)
        setContentView(root)
    }

    private fun renderChips(ink: Int, soft: Int) {
        chips.removeAllViews()
        for (c in if (ch == "Received") inCats else outCats) {
            val on = c == cat
            val t = label(c, 13f, if (on) Color.WHITE else ink)
            t.setPadding(dp(14), dp(8), dp(14), dp(8))
            t.background = shape(if (on) blue else soft, 18)
            t.setOnClickListener { cat = c; renderChips(ink, soft) }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(8)
            chips.addView(t, lp)
        }
    }

    private fun takePlan(amt: Double): JSONObject? {
        val p = getSharedPreferences("pesa", Context.MODE_PRIVATE)
        val arr = try { JSONArray(p.getString("plans", "[]")) } catch (e: Exception) { return null }
        val used = JSONArray(p.getString("pu", "[]"))
        val usedIds = (0 until used.length()).map { used.getString(it) }.toSet()
        val now = System.currentTimeMillis()
        var best: JSONObject? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (usedIds.contains(o.optString("id"))) continue
            if (now - o.optLong("ts") > 7200000L) continue
            if (Math.abs(o.optDouble("amt") - amt) > 0.01) continue
            val b = best
            if (b == null || o.optLong("ts") > b.optLong("ts")) best = o
        }
        val chosen = best ?: return null
        used.put(chosen.optString("id"))
        p.edit().putString("pu", used.toString()).apply()
        return chosen
    }

    private fun mark(body: String, kind: String, amt: Double, plan: String = "") {
        val p = getSharedPreferences("pesa", Context.MODE_PRIVATE)
        val q = JSONArray(p.getString("q", "[]"))
        for (i in q.length() - 1 downTo 0) {
            val o = q.optJSONObject(i) ?: continue
            if (o.optString("t") == body) {
                o.put("ch", ch).put("cat", cat).put("kind", kind).put("amt", amt).put("auto", true)
                if (plan.isNotEmpty()) o.put("plan", plan)
                break
            }
        }
        p.edit().putString("q", q.toString()).apply()
    }

    private fun budgetCheck(category: String, amt: Double) {
        val p = getSharedPreferences("pesa", Context.MODE_PRIVATE)
        val snap = try { JSONObject(p.getString("bud", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        val limits = snap.optJSONObject("limits") ?: return
        val spent = snap.optJSONObject("spent") ?: JSONObject()
        val oldKeys = snap.optJSONObject("keys") ?: JSONObject()

        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val day = f.format(Calendar.getInstance().time)
        val wk = Calendar.getInstance()
        wk.firstDayOfWeek = Calendar.MONDAY
        wk.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val keys = linkedMapOf("d" to day, "w" to f.format(wk.time), "m" to day.substring(0, 7), "y" to day.substring(0, 4))

        val names = spent.keys().asSequence().toList()
        for ((pk, pkey) in keys) {
            if (oldKeys.optString(pk) != pkey) for (n in names) spent.optJSONObject(n)?.put(pk, 0.0)
        }

        val al = JSONArray(p.getString("al", "[]"))
        val done = (0 until al.length()).map { al.getString(it) }.toMutableSet()
        val words = mapOf("d" to "daily", "w" to "weekly", "m" to "monthly", "y" to "annual")

        for (k in listOf(category, "ALL")) {
            val lim = limits.optJSONObject(k) ?: continue
            val sp = spent.optJSONObject(k) ?: JSONObject().also { spent.put(k, it) }
            for ((pk, pkey) in keys) {
                val l = lim.optDouble(pk, 0.0)
                if (l <= 0) continue
                val after = sp.optDouble(pk, 0.0) + amt
                sp.put(pk, after)
                val id = "$k|$pk|$pkey"
                if (after > l && !done.contains(id)) {
                    done.add(id)
                    val who = if (k == "ALL") "Overall spending" else k
                    Notif.post(
                        this, "Budget passed ⚠️",
                        who + " " + words[pk] + " budget passed: KES " + String.format(Locale.US, "%,.0f", after) +
                            " of " + String.format(Locale.US, "%,.0f", l)
                    )
                }
            }
        }
        val nk = JSONObject()
        for ((pk, pkey) in keys) nk.put(pk, pkey)
        val na = JSONArray()
        for (x in done) na.put(x)
        snap.put("spent", spent)
        snap.put("keys", nk)
        p.edit().putString("bud", snap.toString()).putString("al", na.toString()).apply()
    }
}
