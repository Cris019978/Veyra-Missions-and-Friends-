package com.example.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.MainActivity
import com.example.util.SafeRuntimeBootstrap

/**
 * Fallback diagnostic screen launched by the global UncaughtExceptionHandler.
 * Uses native Android views intentionally so it remains functional even if
 * Compose, ViewModel, or Firebase fail during startup.
 */
class CrashReportActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val reportText = intent?.getStringExtra(EXTRA_REPORT_TEXT)
      ?.takeIf { it.isNotBlank() }
      ?: "Error desconocido sin detalle adicional."
    val threadName = intent?.getStringExtra(EXTRA_THREAD_NAME).orEmpty().ifBlank { "main" }

    val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES

    val bgColor = if (isDark) Color.parseColor("#121316") else Color.parseColor("#F8F9FA")
    val headerBg = if (isDark) Color.parseColor("#5C1919") else Color.parseColor("#FFDAD6")
    val headerFg = if (isDark) Color.parseColor("#FFDAD6") else Color.parseColor("#410002")
    val cardBg = if (isDark) Color.parseColor("#1E2025") else Color.parseColor("#FFFFFF")
    val cardStroke = if (isDark) Color.parseColor("#3A3D45") else Color.parseColor("#DADCE0")
    val textPrimary = if (isDark) Color.parseColor("#E3E5E8") else Color.parseColor("#1F1F1F")
    val textSecondary = if (isDark) Color.parseColor("#AEB4BD") else Color.parseColor("#5F6368")
    val primaryBtnBg = if (isDark) Color.parseColor("#6DD58C") else Color.parseColor("#006B5E")
    val primaryBtnFg = if (isDark) Color.parseColor("#00382E") else Color.parseColor("#FFFFFF")
    val secondaryBtnBg = if (isDark) Color.parseColor("#2B2F36") else Color.parseColor("#E8EEF2")
    val secondaryBtnFg = if (isDark) Color.parseColor("#E3E5E8") else Color.parseColor("#1B312D")

    fun dp(value: Int): Int = TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      value.toFloat(),
      resources.displayMetrics,
    ).toInt()

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(bgColor)
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    }

    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
      v.setPadding(bars.left + dp(16), bars.top + dp(16), bars.right + dp(16), bars.bottom + dp(16))
      insets
    }

    val headerCard = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background = GradientDrawable().apply {
        setColor(headerBg)
        cornerRadius = dp(18).toFloat()
      }
      setPadding(dp(16), dp(14), dp(16), dp(14))
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      ).apply { bottomMargin = dp(12) }
    }

    val titleView = TextView(this).apply {
      text = "Veyra detectó un error inesperado"
      setTextColor(headerFg)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
      setTypeface(typeface, Typeface.BOLD)
    }

    val subtitleView = TextView(this).apply {
      text = "Hilo: $threadName • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
      setTextColor(headerFg)
      alpha = 0.88f
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setPadding(0, dp(4), 0, 0)
    }

    headerCard.addView(titleView)
    headerCard.addView(subtitleView)
    root.addView(headerCard)

    val hintLabel = TextView(this).apply {
      text = "Detalle técnico completo (desplazable y seleccionable):"
      setTextColor(textSecondary)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      ).apply { bottomMargin = dp(6) }
    }
    root.addView(hintLabel)

    val scrollView = ScrollView(this).apply {
      isFillViewport = true
      background = GradientDrawable().apply {
        setColor(cardBg)
        setStroke(dp(1), cardStroke)
        cornerRadius = dp(16).toFloat()
      }
      setPadding(dp(12), dp(12), dp(12), dp(12))
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        0,
        1f,
      ).apply { bottomMargin = dp(14) }
    }

    val traceView = TextView(this).apply {
      id = TRACE_TEXT_VIEW_ID
      text = reportText
      setTextColor(textPrimary)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      typeface = Typeface.MONOSPACE
      setTextIsSelectable(true)
      setLineSpacing(dp(2).toFloat(), 1.15f)
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      )
    }
    scrollView.addView(traceView)
    root.addView(scrollView)

    val actionsRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
      )
    }

    val copyButton = Button(this).apply {
      id = COPY_BUTTON_ID
      text = "Copiar"
      contentDescription = "Copiar reporte de error"
      setTextColor(secondaryBtnFg)
      isAllCaps = false
      background = GradientDrawable().apply {
        setColor(secondaryBtnBg)
        cornerRadius = dp(24).toFloat()
      }
      minimumHeight = dp(48)
      layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply {
        marginEnd = dp(8)
      }
      setOnClickListener {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Veyra Crash Report", reportText))
        Toast.makeText(this@CrashReportActivity, "Reporte copiado al portapapeles", Toast.LENGTH_SHORT).show()
      }
    }

    val retryButton = Button(this).apply {
      id = RETRY_BUTTON_ID
      text = "Reintentar"
      contentDescription = "Reintentar abrir la aplicación"
      setTextColor(primaryBtnFg)
      isAllCaps = false
      background = GradientDrawable().apply {
        setColor(primaryBtnBg)
        cornerRadius = dp(24).toFloat()
      }
      minimumHeight = dp(48)
      layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply {
        marginStart = dp(8)
      }
      setOnClickListener {
        SafeRuntimeBootstrap.clearCrashCooldown(applicationContext)
        val restartIntent = Intent(this@CrashReportActivity, MainActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(restartIntent)
        finish()
      }
    }

    actionsRow.addView(copyButton)
    actionsRow.addView(retryButton)
    root.addView(actionsRow)

    setContentView(root)
  }

  companion object {
    const val EXTRA_REPORT_TEXT = "extra_crash_report_text"
    const val EXTRA_THREAD_NAME = "extra_crash_thread_name"
    const val TRACE_TEXT_VIEW_ID = 0x564501
    const val COPY_BUTTON_ID = 0x564502
    const val RETRY_BUTTON_ID = 0x564503

    fun createIntent(
      context: Context,
      threadName: String,
      reportText: String,
    ): Intent {
      return Intent(context, CrashReportActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        putExtra(EXTRA_THREAD_NAME, threadName)
        putExtra(EXTRA_REPORT_TEXT, reportText)
      }
    }
  }
}
