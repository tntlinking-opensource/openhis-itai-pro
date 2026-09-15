/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.temperaturesheet.support;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 体温单记录时点与三测槽辅助（AS-IP-NUR-007 · BR-IP-TMP-003）。
 *
 * <p>图表仍按固定 6 槽 {@code 02/06/10/14/18/22} 分列；保存允许 {@code HH:mm} 或历史槽 code。
 * 体温/脉搏点与入院标记落格按 30 分界就近整点（09:40 → 10 槽）。
 */
@Component
public class TemperatureSheetRecordedAtResolver {

    /** 三测固定时点槽（小时，两位字符串）。 */
    public static final List<String> TIME_SLOTS = Collections.unmodifiableList(
        Arrays.asList("02", "06", "10", "14", "18", "22"));

    /**
     * 将业务日期与时点组装为记录时点。
     *
     * @param recordDate 业务日期
     * @param timeSlot   历史槽 code（02～22）或 {@code HH:mm}
     * @return 本地日期时间（可含分钟）
     * @throws BusinessException 时点非法
     */
    public LocalDateTime toRecordedAt(LocalDate recordDate, String timeSlot) {
        // Gate：日期与时点均必填
        if (recordDate == null || StringUtils.isEmpty(timeSlot)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_TMP_201,
                "请检查日期与测量时点");
        }
        String normalized = timeSlot.trim();
        // 业务分支：兼容历史固定 6 槽 code（整点）
        if (TIME_SLOTS.contains(normalized)) {
            int hour = Integer.parseInt(normalized);
            return recordDate.atTime(hour, 0);
        }
        LocalTime clock = parseClockTime(normalized);
        if (clock == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_TMP_201,
                "请检查日期与测量时点");
        }
        return recordDate.atTime(clock.getHour(), clock.getMinute(), 0);
    }

    /**
     * 从记录时点反推图表三测槽（30 分界就近整点后再归桶）。
     *
     * <p>会跨槽的半点仅六个：01:30、05:30、09:30、13:30、17:30、21:30。
     * 例如 09:40 落 10 槽，09:29 仍落 06 槽。
     *
     * @param recordedAt 记录时点
     * @return 槽 code；无法匹配时返回 {@code null}
     */
    public String extractTimeSlot(LocalDateTime recordedAt) {
        if (recordedAt == null) {
            return null;
        }
        return resolveChartTimeSlotFromClock(recordedAt.toLocalTime());
    }

    /**
     * 将记录时点格式化为 {@code HH:mm}（录入回显用）。
     *
     * @param recordedAt 记录时点
     * @return 时分；空时返回 {@code null}
     */
    public String formatRecordTimeHm(LocalDateTime recordedAt) {
        if (recordedAt == null) {
            return null;
        }
        return recordedAt.toLocalTime().format(GeneralSharedConstants.TIME_HH_MM_FORMATTER);
    }

    /**
     * 返回当前槽在固定序列中的下一槽（最后一槽返回 {@code null}）。
     *
     * @param currentTimeSlot 当前槽 code 或 {@code HH:mm}
     * @return 下一槽或 {@code null}
     */
    public String resolveNextTimeSlot(String currentTimeSlot) {
        if (StringUtils.isEmpty(currentTimeSlot)) {
            return null;
        }
        String normalized = currentTimeSlot.trim();
        String chartSlot = normalized;
        // 业务分支：若传入时分，先按就近整点归到图表槽再取下一槽
        if (!TIME_SLOTS.contains(normalized)) {
            LocalTime clock = parseClockTime(normalized);
            if (clock == null) {
                return null;
            }
            chartSlot = resolveChartTimeSlotFromClock(clock);
        }
        for (int index = 0; index < TIME_SLOTS.size(); index++) {
            if (TIME_SLOTS.get(index).equals(chartSlot)) {
                // 业务分支：22 槽为末槽，前端不再自动聚焦
                if (index + 1 >= TIME_SLOTS.size()) {
                    return null;
                }
                return TIME_SLOTS.get(index + 1);
            }
        }
        return null;
    }

    /**
     * 解析窗口天数（3/7/14）。
     *
     * @param windowRange 窗口 code（3d/7d/14d）
     * @return 天数
     * @throws BusinessException 非法窗口
     */
    public int resolveWindowDays(String windowRange) {
        if (InpatientNurseStationConstants.TEMPERATURE_SHEET_WINDOW_3D.equals(windowRange)) {
            return 3;
        }
        if (InpatientNurseStationConstants.TEMPERATURE_SHEET_WINDOW_7D.equals(windowRange)) {
            return 7;
        }
        if (InpatientNurseStationConstants.TEMPERATURE_SHEET_WINDOW_14D.equals(windowRange)) {
            return 14;
        }
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_TMP_201,
            "请检查日期与测量时点");
    }

    /**
     * 按 30 分界就近整点后映射三测图表槽（与前端图表落格一致）。
     *
     * @param clock 时分
     * @return 槽 code；clock 为空时返回 {@code null}
     */
    public String resolveChartTimeSlotFromClock(LocalTime clock) {
        if (clock == null) {
            return null;
        }
        int hour = clock.getHour();
        int minute = clock.getMinute();
        // 半点起进位；23:30 进到 00:00 仍落当日 22 槽
        if (minute >= 30) {
            hour = (hour + 1) % 24;
        }
        return resolveChartTimeSlotFromHour(hour);
    }

    /**
     * 小时映射到三测图表槽（整点已就近处理后的归桶）。
     *
     * @param hour 小时 0～23
     * @return 槽 code
     */
    public String resolveChartTimeSlotFromHour(int hour) {
        if (hour >= 2 && hour <= 5) {
            return "02";
        }
        if (hour >= 6 && hour <= 9) {
            return "06";
        }
        if (hour >= 10 && hour <= 13) {
            return "10";
        }
        if (hour >= 14 && hour <= 17) {
            return "14";
        }
        if (hour >= 18 && hour <= 21) {
            return "18";
        }
        return "22";
    }

    /**
     * 解析 {@code HH:mm}（兼容误传 {@code HH:mm:ss}，秒忽略）。
     *
     * @param text 时点文本
     * @return 本地时间；非法返回 {@code null}
     */
    private LocalTime parseClockTime(String text) {
        try {
            return LocalTime.parse(text, GeneralSharedConstants.TIME_HH_MM_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // 继续尝试 HH:mm:ss
        }
        try {
            LocalTime withSeconds = LocalTime.parse(text, GeneralSharedConstants.TIME_HH_MM_SS_FORMATTER);
            return LocalTime.of(withSeconds.getHour(), withSeconds.getMinute());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
