package org.stypox.dicio.skills.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.R
import org.stypox.dicio.sentences.Sentences.Alarm
import org.stypox.dicio.util.getString
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class AlarmSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Alarm>) :
    StandardRecognizerSkill<Alarm>(correspondingSkillInfo, data) {
    override suspend fun generateOutput(ctx: SkillContext, inputData: Alarm): SkillOutput {
        return when (inputData) {
            is Alarm.Set -> {
                val time = inputData.time?.let {
                    ctx.parserFormatter?.extractDateTime(it)?.parseFirst()?.toLocalTime()
                }

                if (time == null) {
                    AlarmOutput.SetAskTime { setAlarm(ctx, it, inputData.name) }
                }
                else {
                    setAlarm(ctx, time, inputData.name)
                }
            }
            is Alarm.QueryNext -> {
                // To-Do: support querying specific alarms
                queryNextAlarm(ctx)
            }
            is Alarm.QueryAll -> {
                queryAllAlarms(ctx)
            }
            is Alarm.Cancel -> {
                val time = inputData.time?.let {
                    ctx.parserFormatter?.extractDateTime(it)?.parseFirst()?.toLocalTime()
                }

                cancelAlarm(ctx, time, inputData.name)
            }
        }
    }

    private fun setAlarm(
        ctx: SkillContext,
        time: LocalTime,
        name: String?
    ): SkillOutput {
        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM)
        alarmIntent.putExtra(AlarmClock.EXTRA_HOUR, time.hour)
        alarmIntent.putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        alarmIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        ctx.android.startActivity(alarmIntent)

        return AlarmOutput.Set(
            time,
            name
        )
    }

    private fun queryAllAlarms(
        ctx: SkillContext
    ): SkillOutput {
        val alarmIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.android.startActivity(alarmIntent)
        return AlarmOutput.QueryAll()

    }

    private fun queryNextAlarm(
        ctx: SkillContext
    ): SkillOutput {
        val alarmManager = ctx.android.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // This could be an alarm set by a non-Clock app
        // but we will leave this feature in as-is for now
        val alarmClock = alarmManager.nextAlarmClock

        if (alarmClock == null) {
            return AlarmOutput.QueryNone()
        }
        else {
            val longTimeSeconds = alarmClock.triggerTime / 1000
            val zoneId = ZoneId.systemDefault()
            val localTime = LocalDateTime
                .ofEpochSecond(longTimeSeconds, 0, ZoneOffset.UTC)
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(zoneId)
                .toLocalTime()
            return AlarmOutput.QueryNext(localTime)
        }
    }

    private fun cancelAlarm(
        ctx: SkillContext,
        time: LocalTime?,
        name: String?
    ): SkillOutput {
        val alarmIntent = Intent(AlarmClock.ACTION_DISMISS_ALARM)

        val alarmOutput : AlarmOutput

        if (time != null) {
            // Filter for a given time on the alarm
            alarmIntent.putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
            alarmIntent.putExtra(AlarmClock.EXTRA_HOUR, time.hour)
            alarmIntent.putExtra(AlarmClock.EXTRA_MINUTES, time.minute)

            alarmOutput = AlarmOutput.CancelTime(time)
        }
        else {
            // Cancel all alarms if no time specified
            // To-Do: only cancel next alarm
            alarmIntent.putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_ALL)
            alarmOutput = AlarmOutput.CancelAll(
                    ctx.getString(R.string.skill_alarm_all_canceled)
            )
        }
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // this will not work with stock or AOSP DeskClock
        // but will work with any fork that implements SkipUI support
        // for dismiss_alarm action
        alarmIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, true)

        ctx.android.startActivity(alarmIntent)

        return alarmOutput
    }

}