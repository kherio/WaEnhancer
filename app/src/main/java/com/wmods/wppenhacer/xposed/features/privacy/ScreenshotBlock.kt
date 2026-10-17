package com.wmods.wppenhacer.xposed.features.privacy

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method

/**
 * Bloquea capturas de pantalla, grabación de pantalla y la miniatura de "recientes" en los chats
 * elegidos (FLAG_SECURE). El chat se activa desde su pantalla de información.
 */
class ScreenshotBlock(
    classLoader: ClassLoader,
    preferences: SharedPreferences
) : Feature(classLoader, preferences) {

    private lateinit var chatUserJidMethod: Method
    private lateinit var groupUserJidMethod: Method

    override fun getPluginName(): String = "Screenshot Block"

    override fun doHook() {
        if (!xprefs.getBoolean("secure_chats", false)) return

        val contactInfoClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, ".ContactInfoActivity"
        )
        val groupInfoClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, ".GroupChatInfoActivity"
        )
        val userJidClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, "jid.UserJid"
        )
        val groupJidClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, "jid.GroupJid"
        )
        chatUserJidMethod = ReflectionUtils.findMethodUsingFilter(contactInfoClass) { m ->
            m.parameterCount == 0 && userJidClass.isAssignableFrom(m.returnType)
        }
        groupUserJidMethod = ReflectionUtils.findMethodUsingFilter(groupInfoClass) { m ->
            m.parameterCount == 0 && groupJidClass.isAssignableFrom(m.returnType)
        }

        WppCore.addListenerActivity(object : WppCore.ActivityChangeState {
            override fun onChange(activity: Activity, type: WppCore.ActivityChangeState.ChangeType) {
                try {
                    when (type) {
                        WppCore.ActivityChangeState.ChangeType.STARTED -> {
                            if (contactInfoClass.isInstance(activity) || groupInfoClass.isInstance(activity)) {
                                addInfoItem(activity, contactInfoClass.isInstance(activity))
                            }
                        }

                        WppCore.ActivityChangeState.ChangeType.RESUMED -> {
                            if (WppCore.getCurrentConversation() === activity) applyToConversation(activity)
                        }

                        else -> {}
                    }
                } catch (e: Throwable) {
                    logDebug(e)
                }
            }
        })
    }

    private fun prefKey(number: String) = "${number}_secure"

    /** El chat puede tardar un instante en estar listo, por eso se reintenta un par de veces. */
    private fun applyToConversation(activity: Activity) {
        val handler = Handler(Looper.getMainLooper())
        for (delay in longArrayOf(0, 300, 1000)) {
            handler.postDelayed({
                try {
                    if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                    val number = WppCore.getCurrentUserJid()?.phoneNumber ?: return@postDelayed
                    setSecure(activity, WppCore.getPrivBoolean(prefKey(number), false))
                } catch (e: Throwable) {
                    logDebug(e)
                }
            }, delay)
        }
    }

    /** Se usa setAttributes y no addFlags/setFlags porque esos están enganchados por "permitir capturas". */
    private fun setSecure(activity: Activity, secure: Boolean) {
        val window = activity.window
        val lp = window.attributes
        val has = lp.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        if (has == secure) return
        lp.flags = if (secure) {
            lp.flags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            lp.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
        }
        window.attributes = lp
    }

    @SuppressLint("ResourceType")
    private fun addInfoItem(activity: Activity, isChat: Boolean) {
        if (activity.findViewById<View>(ITEM_ID) != null) return
        val id = Utils.getID("contact_info_security_card_layout", "id")
        val infoLayout = activity.window.findViewById<ViewGroup>(id) ?: return
        val jid = FMessageWpp.UserJid(
            ReflectionUtils.callMethod(if (isChat) chatUserJidMethod else groupUserJidMethod, activity)
        )
        val number = jid.phoneNumber ?: return

        val icon = activity.getDrawable(R.drawable.ic_privacy)!!
        icon.setTint(0xff8696a0.toInt())

        val row = LinearLayout(activity).apply {
            id = ITEM_ID
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 16, 16, 16)
        }
        val image = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(Utils.dipToPixels(20), Utils.dipToPixels(20)).apply {
                setMargins(Utils.dipToPixels(20), 0, Utils.dipToPixels(16), Utils.dipToPixels(20))
            }
            setImageDrawable(icon)
        }
        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 16 }
        }
        val title = TextView(activity).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            text = activity.getString(R.string.secure_chat)
            setTextColor(DesignUtils.getPrimaryTextColor())
        }
        val summary = TextView(activity).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        fun refresh() {
            summary.text = activity.getString(
                if (WppCore.getPrivBoolean(prefKey(number), false)) R.string.secure_chat_on else R.string.secure_chat_off
            )
        }
        refresh()
        texts.addView(title)
        texts.addView(summary)
        row.addView(image)
        row.addView(texts)
        row.setOnClickListener {
            val enabled = !WppCore.getPrivBoolean(prefKey(number), false)
            WppCore.setPrivBoolean(prefKey(number), enabled)
            refresh()
            Utils.showToast(
                activity.getString(if (enabled) R.string.secure_chat_on else R.string.secure_chat_off),
                Toast.LENGTH_SHORT
            )
        }
        infoLayout.addView(row)
    }

    private companion object {
        private const val ITEM_ID = 0x7f0a9998
    }
}
