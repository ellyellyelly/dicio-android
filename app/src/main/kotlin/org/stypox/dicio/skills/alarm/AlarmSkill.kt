package org.stypox.dicio.skills.alarm

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
import java.time.LocalTime

class AlarmSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Alarm>) :
    StandardRecognizerSkill<Alarm>(correspondingSkillInfo, data) {
    override suspend fun generateOutput(ctx: SkillContext, inputData: Alarm): SkillOutput {
        return when (inputData) {
            is Alarm.Set -> {
                val time = inputData.time?.let {
                    ctx.parserFormatter?.extractDateTime(it)?.first?.toLocalTime()
                }

                if (time == null) {
                    AlarmOutput.SetAskTime { setAlarm(ctx, it, inputData.name) }
                }
                else {
                    setAlarm(ctx, time, inputData.name)
                }
            }
            is Alarm.Query -> {
                // To-Do: support querying specific alarms
                queryAlarm(ctx, null)
            }
            is Alarm.Cancel -> {
                val time = inputData.time?.let {
                    ctx.parserFormatter?.extractDateTime(it)?.first?.toLocalTime()
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
        ctx.android.startActivity(alarmIntent);

        return AlarmOutput.Set(
            time,
            name
        )
    }

    private fun queryAlarm(
        ctx: SkillContext,
        name: String?
    ): SkillOutput {
        TODO()
    }

    private fun cancelAlarm(
        ctx: SkillContext,
        time: LocalTime?,
        name: String?
    ): SkillOutput {
        val alarmIntent = Intent(AlarmClock.ACTION_DISMISS_ALARM)
        // To-Do: filter for alarm type and time
        alarmIntent.putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_ALL)
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // this will not work with stock or AOSP DeskClock
        // but will work with any fork that implements SkipUI support
        // for dismiss_alarm action
        alarmIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, true)

        ctx.android.startActivity(alarmIntent);

        return AlarmOutput.Cancel(
            ctx.getString(R.string.skill_alarm_all_canceled)
        )
    }

}