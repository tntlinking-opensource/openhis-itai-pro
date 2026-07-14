/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.support;

import com.openhis.whale.job.domain.CronPresetType;
import com.openhis.whale.job.domain.ScheduledJobMisfirePolicy;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Cron 表达式校验、可视化拼装与可读描述。
 */
public final class CronExpressionSupport {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private CronExpressionSupport() {
    }

    /**
     * 校验 Cron 表达式是否合法。
     *
     * @param cronExpression Cron 表达式
     * @return 是否合法
     */
    public static boolean isValid(String cronExpression) {
        return CronExpression.isValidExpression(cronExpression);
    }

    /**
     * 返回 Cron 非法时的错误信息，合法时返回 null。
     *
     * @param cronExpression Cron 表达式
     * @return 错误信息或 null
     */
    public static String getInvalidMessage(String cronExpression) {
        try {
            new CronExpression(cronExpression);
            return null;
        } catch (ParseException parseException) {
            return parseException.getMessage();
        }
    }

    /**
     * 计算下一次触发时间。
     *
     * @param cronExpression Cron 表达式
     * @return 下次执行时间，无法计算时返回 null
     */
    public static LocalDateTime getNextExecution(String cronExpression) {
        try {
            CronExpression cron = new CronExpression(cronExpression);
            Date next = cron.getNextValidTimeAfter(new Date());
            if (next == null) {
                return null;
            }
            return LocalDateTime.ofInstant(next.toInstant(), DEFAULT_ZONE);
        } catch (ParseException parseException) {
            return null;
        }
    }

    /**
     * 计算未来若干次触发时间。
     *
     * @param cronExpression Cron 表达式
     * @param count          次数上限
     * @return 触发时间列表
     */
    public static List<LocalDateTime> getNextExecutions(String cronExpression, int count) {
        List<LocalDateTime> result = new ArrayList<>();
        if (count <= 0) {
            return result;
        }
        try {
            CronExpression cron = new CronExpression(cronExpression);
            Date cursor = new Date();
            for (int index = 0; index < count; index++) {
                Date next = cron.getNextValidTimeAfter(cursor);
                if (next == null) {
                    break;
                }
                result.add(LocalDateTime.ofInstant(next.toInstant(), DEFAULT_ZONE));
                cursor = next;
            }
        } catch (ParseException parseException) {
            return result;
        }
        return result;
    }

    /**
     * 将运维预设选项转换为 Quartz Cron 表达式（6 段，含秒）。
     *
     * @param presetType       预设类型 code
     * @param intervalMinutes  每 N 分钟（EVERY_N_MINUTES）
     * @param minute           分钟（HOURLY/DAILY/WEEKLY/MONTHLY）
     * @param hour             小时
     * @param dayOfWeek        星期（1=周日 … 7=周六，与 Quartz 一致）
     * @param dayOfMonth       日（1–31）
     * @param customExpression 自定义表达式（CUSTOM）
     * @return Cron 表达式
     */
    public static String buildFromPreset(String presetType, Integer intervalMinutes, Integer minute,
                                         Integer hour, Integer dayOfWeek, Integer dayOfMonth,
                                         String customExpression) {
        CronPresetType preset = CronPresetType.fromCode(presetType);
        if (preset == null) {
            throw new IllegalArgumentException("不支持的 Cron 预设类型: " + presetType);
        }
        if (preset == CronPresetType.CUSTOM) {
            if (customExpression == null || customExpression.trim().isEmpty()) {
                throw new IllegalArgumentException("自定义 Cron 表达式不能为空");
            }
            return customExpression.trim();
        }
        if (preset == CronPresetType.EVERY_N_MINUTES) {
            int interval = intervalMinutes == null ? 1 : intervalMinutes;
            if (interval < 1 || interval > 59) {
                throw new IllegalArgumentException("间隔分钟须在 1–59 之间");
            }
            return "0 0/" + interval + " * * * ?";
        }
        if (preset == CronPresetType.HOURLY) {
            int minuteValue = minute == null ? 0 : minute;
            return "0 " + minuteValue + " * * * ?";
        }
        int hourValue = hour == null ? 0 : hour;
        int minuteValue = minute == null ? 0 : minute;
        if (preset == CronPresetType.DAILY) {
            return "0 " + minuteValue + " " + hourValue + " * * ?";
        }
        if (preset == CronPresetType.WEEKLY) {
            int weekValue = dayOfWeek == null ? 2 : dayOfWeek;
            return "0 " + minuteValue + " " + hourValue + " ? * " + weekValue;
        }
        if (preset == CronPresetType.MONTHLY) {
            int monthDay = dayOfMonth == null ? 1 : dayOfMonth;
            return "0 " + minuteValue + " " + hourValue + " " + monthDay + " * ?";
        }
        throw new IllegalArgumentException("不支持的 Cron 预设类型: " + presetType);
    }

    /**
     * 生成面向运维人员的 Cron 中文描述（简要）。
     *
     * @param cronExpression Cron 表达式
     * @return 描述文本
     */
    public static String describe(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return "";
        }
        String trimmed = cronExpression.trim();
        if ("0 0/1 * * * ?".equals(trimmed) || "0 0/1 * * * ?".equals(trimmed)) {
            return "每分钟执行";
        }
        if (trimmed.startsWith("0 0/")) {
            String part = trimmed.substring("0 0/".length(), trimmed.indexOf(' ', "0 0/".length()));
            return "每 " + part + " 分钟执行";
        }
        if (trimmed.matches("0 \\d+ \\* \\* \\* \\?")) {
            String minutePart = trimmed.split(" ")[1];
            return "每小时的第 " + minutePart + " 分执行";
        }
        if (trimmed.matches("0 \\d+ \\d+ \\* \\* \\?")) {
            String[] parts = trimmed.split(" ");
            return "每天 " + parts[2] + ":" + padMinute(parts[1]) + " 执行";
        }
        LocalDateTime next = getNextExecution(trimmed);
        if (next == null) {
            return "Cron: " + trimmed;
        }
        return "Cron: " + trimmed + "（下次约 " + next + "）";
    }

    /**
     * 按策略 code 配置 CronScheduleBuilder 的 Misfire 行为。
     *
     * @param builder       原始 builder
     * @param misfirePolicy 策略 code
     * @return 配置后的 builder
     */
    public static CronScheduleBuilder applyMisfirePolicy(CronScheduleBuilder builder, String misfirePolicy) {
        ScheduledJobMisfirePolicy policy = ScheduledJobMisfirePolicy.fromCode(misfirePolicy);
        if (policy == null || policy == ScheduledJobMisfirePolicy.DEFAULT) {
            return builder;
        }
        if (policy == ScheduledJobMisfirePolicy.IGNORE_MISFIRES) {
            return builder.withMisfireHandlingInstructionIgnoreMisfires();
        }
        if (policy == ScheduledJobMisfirePolicy.FIRE_AND_PROCEED) {
            return builder.withMisfireHandlingInstructionFireAndProceed();
        }
        if (policy == ScheduledJobMisfirePolicy.DO_NOTHING) {
            return builder.withMisfireHandlingInstructionDoNothing();
        }
        return builder;
    }

    private static String padMinute(String minute) {
        if (minute.length() == 1) {
            return "0" + minute;
        }
        return minute;
    }
}
