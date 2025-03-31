package org.stypox.dicio.skills.alarm

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import org.dicio.numbers.ParserFormatter
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.AlwaysBestScore
import org.dicio.skill.skill.AlwaysWorstScore
import org.dicio.skill.skill.InteractionPlan
import org.dicio.skill.skill.Score
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.skill.Specificity
import org.stypox.dicio.R
import org.stypox.dicio.io.graphical.Headline
import org.stypox.dicio.io.graphical.HeadlineSpeechSkillOutput
import org.stypox.dicio.util.getString
import java.time.LocalTime

sealed interface AlarmOutput : SkillOutput {
    class Set(
        private val time: LocalTime,
        private val name: String?

    ) : AlarmOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = formatStringWithName(
            ctx, name, time, R.string.skill_alarm_set, R.string.skill_alarm_set_name
        )

        @Composable
        override fun GraphicalOutput(ctx: SkillContext) {
            Headline(
                text = getSpeechOutput(ctx)
            )
        }

    }

    class SetAskTime(
        private val onGotTime: suspend (LocalTime) -> SkillOutput
    ) : AlarmOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String =
            ctx.getString(R.string.skill_alarm_when_to_set)

        override fun getInteractionPlan(ctx: SkillContext): InteractionPlan {
            val timeSkill = object : Skill<LocalTime?>(AlarmInfo, Specificity.HIGH) {
                override fun score(ctx: SkillContext, input: String): Pair<Score, LocalTime?> {
                    val time = ctx.parserFormatter!!.extractDateTime(input).first?.toLocalTime()
                    return Pair(
                        if (time == null) AlwaysWorstScore else AlwaysBestScore,
                        time
                    )
                }

                override suspend fun generateOutput(
                    ctx: SkillContext,
                    inputData: LocalTime?
                ): SkillOutput {
                    return if (inputData == null) {
                        throw RuntimeException("AlwaysWorstScore still triggered generateOutput")
                    }
                    else {
                        onGotTime(inputData)
                    }
                }
            }

            return InteractionPlan.StartSubInteraction(
                reopenMicrophone = true,
                nextSkills = listOf(timeSkill)
            )
        }
        // To-Do: finish implementing
    }

    class Cancel(
        private val speechOut: String
    ) : AlarmOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = speechOut
    }

    class Query(
        private val speechOut: String
    ) : AlarmOutput, HeadlineSpeechSkillOutput {
        override fun getSpeechOutput(ctx: SkillContext): String = speechOut
    }
}

fun formatStringWithName(
    ctx: SkillContext,
    name: String?,
    time: LocalTime,
    @StringRes stringWithoutName: Int,
    @StringRes stringWithName: Int
): String {
    val formattedTime = getFormattedTime(ctx.parserFormatter!!, time)
    return if (name == null) {
        ctx.getString(stringWithoutName, formattedTime)
    } else {
        ctx.getString(stringWithName, name, formattedTime)
    }
}

fun getFormattedTime(
    parserFormatter: ParserFormatter,
    time: LocalTime,
): String {
    val formattedTime = parserFormatter
        .niceTime(time)
        .get()
    return formattedTime
}