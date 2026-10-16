package com.wmods.wppenhacer.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.wmods.wppenhacer.toolkit.S
import com.wmods.wppenhacer.toolkit.ToolRequest
import com.wmods.wppenhacer.toolkit.update.UpdateFlow

/**
 * Herramientas de WaToolkit dentro de la app del módulo. Trabajan sobre los datos de WhatsApp,
 * así que cada una abre WhatsApp y lanza allí la herramienta; "buscar actualizaciones" se
 * ejecuta aquí mismo.
 */
class ToolsFragment : Fragment() {

    private class Tool(val icon: String, val title: String, val desc: String, val action: () -> Unit)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

        val list = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        list.addView(TextView(ctx).apply {
            text = S.get(S.hub_title)
            textSize = 22f
            setPadding(dp(4), dp(4), dp(4), dp(4))
        })

        val tools = listOf(
            Tool("📥", S.get(S.import_chat), S.get(S.tool_import_desc)) { open(ToolRequest.IMPORT) },
            Tool("📤", S.get(S.export_menu), S.get(S.tool_export_desc)) { open(ToolRequest.EXPORT) },
            Tool("🗑", S.get(S.deleted_menu), S.get(S.tool_deleted_desc)) { open(ToolRequest.DELETED) },
            Tool("♻️", S.get(S.recover_menu), S.get(S.tool_recover_desc)) { open(ToolRequest.RECOVER) },
            Tool("💾", S.get(S.backup_menu), S.get(S.tool_backup_desc)) { open(ToolRequest.BACKUP) },
            Tool("⬇", S.get(S.update_menu), S.get(S.tool_update_desc)) {
                UpdateFlow(requireActivity()).start()
            }
        )
        for (t in tools) {
            val card = MaterialCardView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
                radius = dp(16).toFloat()
                isClickable = true
                isFocusable = true
                setOnClickListener { t.action() }
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(ctx).apply {
                text = t.icon
                textSize = 26f
                setPadding(0, 0, dp(16), 0)
            })
            val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(ctx).apply {
                text = t.title
                textSize = 16f
            })
            texts.addView(TextView(ctx).apply {
                text = t.desc
                textSize = 13f
                alpha = 0.7f
            })
            row.addView(texts)
            card.addView(row)
            list.addView(card)
        }
        return NestedScrollView(ctx).apply { addView(list) }
    }

    /** Abre WhatsApp (o WhatsApp Business, si es el único o el elegido) con la herramienta. */
    private fun open(tool: String) {
        val ctx = requireContext()
        val installed = listOf("com.whatsapp", "com.whatsapp.w4b").filter {
            try {
                ctx.packageManager.getPackageInfo(it, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
        when (installed.size) {
            0 -> Toast.makeText(ctx, "WhatsApp", Toast.LENGTH_SHORT).show()
            1 -> launch(tool, installed[0])
            else -> AlertDialog.Builder(ctx)
                .setItems(arrayOf("WhatsApp", "WhatsApp Business")) { _, which ->
                    launch(tool, installed[which])
                }
                .show()
        }
    }

    private fun launch(tool: String, pkg: String) {
        if (!ToolRequest.request(requireContext(), tool, pkg)) {
            Toast.makeText(requireContext(), "WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }
}
