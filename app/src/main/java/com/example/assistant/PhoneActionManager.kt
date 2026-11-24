package com.example.assistant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

sealed class PendingPhoneAction(
    val title: String,
    val description: String,
    val isSensitive: Boolean = true
) {
    data object OpenYouTube : PendingPhoneAction(
        title = "Open YouTube",
        description = "Launch the YouTube application on your device.",
        isSensitive = false
    )

    data class OpenWhatsApp(val phoneOrMessage: String? = null) : PendingPhoneAction(
        title = "Open WhatsApp",
        description = if (phoneOrMessage.isNullOrBlank()) "Launch WhatsApp." else "Open WhatsApp with: $phoneOrMessage",
        isSensitive = true
    )

    data class OpenDialer(val phoneNumber: String = "") : PendingPhoneAction(
        title = if (phoneNumber.isBlank()) "Open Phone Dialer" else "Call / Dial $phoneNumber",
        description = if (phoneNumber.isBlank()) "Open your phone dialer." else "Open your phone dialer pre-filled with $phoneNumber. You control when to place the call.",
        isSensitive = true
    )

    data class CreateSmsDraft(val phoneNumber: String = "", val body: String = "") : PendingPhoneAction(
        title = "Create SMS Draft",
        description = "Open your Messages app with a draft${if (phoneNumber.isNotBlank()) " to $phoneNumber" else ""}.",
        isSensitive = true
    )

    data object OpenCamera : PendingPhoneAction(
        title = "Open Camera",
        description = "Launch the device camera app.",
        isSensitive = true
    )

    data object OpenSettings : PendingPhoneAction(
        title = "Open Device Settings",
        description = "Open Android system settings.",
        isSensitive = false
    )

    data object OpenGallery : PendingPhoneAction(
        title = "Open Gallery",
        description = "Open your device photo gallery.",
        isSensitive = false
    )

    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : PendingPhoneAction(
        title = "Set Alarm for %02d:%02d".format(hour, minute),
        description = "Create an alarm labeled \"$label\" in your Clock app.",
        isSensitive = true
    )

    data class OpenInstalledApp(val appName: String) : PendingPhoneAction(
        title = "Open $appName",
        description = "Find and launch installed app matching \"$appName\".",
        isSensitive = true
    )
}

object PhoneActionManager {

    fun detectActionFromPrompt(prompt: String): PendingPhoneAction? {
        val lower = prompt.lowercase().trim()
        return when {
            lower.contains("open youtube") || lower.contains("youtube kholo") || lower.contains("youtube chalao") ->
                PendingPhoneAction.OpenYouTube
            lower.contains("open whatsapp") || lower.contains("whatsapp kholo") ->
                PendingPhoneAction.OpenWhatsApp()
            lower.contains("open camera") || lower.contains("camera kholo") ->
                PendingPhoneAction.OpenCamera
            lower.contains("open settings") || lower.contains("settings kholo") ->
                PendingPhoneAction.OpenSettings
            lower.contains("open gallery") || lower.contains("gallery kholo") ->
                PendingPhoneAction.OpenGallery
            lower.startsWith("call ") || lower.contains("open dialer") || lower.contains("dial ") -> {
                val digits = Regex("[+0-9][0-9\\- ]{2,}").find(prompt)?.value?.trim().orEmpty()
                PendingPhoneAction.OpenDialer(digits)
            }
            lower.contains("sms ") || lower.contains("text message") || lower.contains("send message to") -> {
                val digits = Regex("[+0-9][0-9\\- ]{2,}").find(prompt)?.value?.trim().orEmpty()
                PendingPhoneAction.CreateSmsDraft(digits, "")
            }
            lower.contains("set alarm") || lower.contains("alarm lagao") -> {
                val timeMatch = Regex("(\\d{1,2}):(\\d{2})").find(lower)
                val hour = timeMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
                val minute = timeMatch?.groupValues?.get(2)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                PendingPhoneAction.SetAlarm(hour, minute, "Jarvis Reminder")
            }
            lower.startsWith("open app ") || (lower.startsWith("open ") && lower.length in 6..35 && !lower.contains("?")) -> {
                val target = lower.removePrefix("open app ").removePrefix("open ").trim()
                if (target.isNotBlank() && !target.contains("file") && !target.contains("url")) {
                    PendingPhoneAction.OpenInstalledApp(target)
                } else null
            }
            else -> null
        }
    }

    fun executeAction(context: Context, action: PendingPhoneAction): Result<String> {
        return try {
            when (action) {
                is PendingPhoneAction.OpenYouTube -> {
                    val ytIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                    if (ytIntent != null) {
                        ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(ytIntent)
                        Result.success("Opened YouTube app.")
                    } else {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(webIntent)
                        Result.success("Opened YouTube in browser.")
                    }
                }
                is PendingPhoneAction.OpenWhatsApp -> {
                    val waIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                    if (waIntent != null) {
                        waIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(waIntent)
                        Result.success("Opened WhatsApp.")
                    } else {
                        Result.failure(IllegalStateException("WhatsApp is not installed on this device."))
                    }
                }
                is PendingPhoneAction.OpenDialer -> {
                    val uri = if (action.phoneNumber.isNotBlank()) {
                        Uri.parse("tel:${Uri.encode(action.phoneNumber)}")
                    } else {
                        Uri.parse("tel:")
                    }
                    val dialIntent = Intent(Intent.ACTION_DIAL, uri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(dialIntent)
                    Result.success("Opened Phone Dialer.")
                }
                is PendingPhoneAction.CreateSmsDraft -> {
                    val smsUri = Uri.parse("smsto:${Uri.encode(action.phoneNumber)}")
                    val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                        putExtra("sms_body", action.body)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(smsIntent)
                    Result.success("Opened SMS draft composer.")
                }
                is PendingPhoneAction.OpenCamera -> {
                    val camIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(camIntent)
                    Result.success("Opened Camera.")
                }
                is PendingPhoneAction.OpenSettings -> {
                    val settingsIntent = Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(settingsIntent)
                    Result.success("Opened Android Settings.")
                }
                is PendingPhoneAction.OpenGallery -> {
                    val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
                        type = "image/*"
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(galleryIntent)
                    Result.success("Opened Gallery.")
                }
                is PendingPhoneAction.SetAlarm -> {
                    val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(alarmIntent)
                    Result.success("Opened Clock app to confirm alarm at %02d:%02d.".format(action.hour, action.minute))
                }
                is PendingPhoneAction.OpenInstalledApp -> {
                    val pm = context.packageManager
                    val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
                    val matches = pm.queryIntentActivities(mainIntent, 0)
                    val matched = matches.firstOrNull {
                        it.loadLabel(pm).toString().contains(action.appName, ignoreCase = true)
                    }
                    if (matched != null) {
                        val launchIntent = pm.getLaunchIntentForPackage(matched.activityInfo.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            Result.success("Launched ${matched.loadLabel(pm)}.")
                        } else {
                            Result.failure(IllegalStateException("App '${action.appName}' has no launchable activity."))
                        }
                    } else {
                        Result.failure(IllegalStateException("No installed app matching '${action.appName}' was found."))
                    }
                }
            }
        } catch (e: ActivityNotFoundException) {
            Result.failure(IllegalStateException("No compatible app is installed to handle ${action.title}."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun tryEvaluateSimpleMath(expression: String): Double? {
        val cleaned = expression.lowercase()
            .removePrefix("calculate")
            .removePrefix("calc")
            .removePrefix("what is")
            .trim()
            .replace("×", "*")
            .replace("÷", "/")
            .replace(" ", "")
        if (cleaned.isEmpty() || !cleaned.all { it.isDigit() || it in "+-*/.()" }) return null
        if (cleaned.none { it in "+-*/" }) return null
        return try {
            object {
                var pos = -1
                var ch = 0
                fun nextChar() { ch = if (++pos < cleaned.length) cleaned[pos].code else -1 }
                fun eat(charToEat: Int): Boolean {
                    if (ch == charToEat) { nextChar(); return true }
                    return false
                }
                fun parse(): Double {
                    nextChar()
                    val x = parseExpression()
                    if (pos < cleaned.length) throw IllegalArgumentException("Unexpected")
                    return x
                }
                fun parseExpression(): Double {
                    var x = parseTerm()
                    while (true) {
                        when {
                            eat('+'.code) -> x += parseTerm()
                            eat('-'.code) -> x -= parseTerm()
                            else -> return x
                        }
                    }
                }
                fun parseTerm(): Double {
                    var x = parseFactor()
                    while (true) {
                        when {
                            eat('*'.code) -> x *= parseFactor()
                            eat('/'.code) -> x /= parseFactor()
                            else -> return x
                        }
                    }
                }
                fun parseFactor(): Double {
                    if (eat('+'.code)) return parseFactor()
                    if (eat('-'.code)) return -parseFactor()
                    val startPos = pos
                    var x: Double
                    if (eat('('.code)) {
                        x = parseExpression()
                        if (!eat(')'.code)) throw IllegalArgumentException("Missing )")
                    } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                        while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                        x = cleaned.substring(startPos, pos).toDouble()
                    } else {
                        throw IllegalArgumentException("Unexpected char")
                    }
                    return x
                }
            }.parse()
        } catch (_: Exception) {
            null
        }
    }
}
