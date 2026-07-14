/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.temperaturesheet.support;

import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 体温单记录时点与三测槽辅助（AS-IP-NUR-007 · BR-IP-TMP-003）。
 *
 * <p>固定 6 槽 {@code 02/06/10/14/18/22}；{@code recorded_at = recordDate + timeSlot 整点}。</p>
 */
@Component
public class TemperatureSheetRecordedAtResolver {

    /** 三测固定时点槽（小时，两位字符串）。 */
    public static final List<String> TIME_SLOTS = Collections.unmodifiableList(
        Arrays.asList("02", "06", "10", "14", "18", "22"));

    /**
     * 将业务日期与时点槽组装为记录时点。
     *
     * @param recordDate 业务日期
     * @param timeSlot   时点槽（02～22）
     * @return 本地日期时间（整点）
     * @throws BusinessException 槽非法
     */
    public LocalDateTime toRecordedAt(LocalDate recordDate, String timeSlot) {
        // Gate：日期与时点槽均必填
        if (recordDate == null || StringUtils.isEmpty(timeSlot)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_TMP_201,
                "请检查日期与测量时点");
        }
        String normalizedSlot = timeSlot.trim();
        // 业务分支：仅允许固定 6 槽，与老系统三测单口径一致
        if (!TIME_SLOTS.contains(normalizedSlot)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_TMP_201,
                "请检查日期与测量时点");
        }
        int hour = Integer.parseInt(normalizedSlot);
        return recordDate.atTime(hour, 0);
    }

    /**
     * 从记录时点反推时点槽字符串。
     *
     * @param recordedAt 记录时点
     * @return 槽 code；无法匹配时返回 {@code null}
     */
    public String extractTimeSlot(LocalDateTime recordedAt) {
        if (recordedAt == null) {
            return null;
        }
        // 业务分支：仅整点且分钟为 0 的时点参与槽匹配
        if (recordedAt.getMinute() != 0 || recordedAt.getSecond() != 0 || recordedAt.getNano() != 0) {
            return null;
        }
        String slot = String.format("%02d", recordedAt.getHour());
        if (TIME_SLOTS.contains(slot)) {
            return slot;
        }
        return null;
    }

    /**
     * 返回当前槽在固定序列中的下一槽（最后一槽返回 {@code null}）。
     *
     * @param currentTimeSlot 当前槽
     * @return 下一槽或 {@code null}
     */
    public String resolveNextTimeSlot(String currentTimeSlot) {
        if (StringUtils.isEmpty(currentTimeSlot)) {
            return null;
        }
        String normalized = currentTimeSlot.trim();
        for (int index = 0; index < TIME_SLOTS.size(); index++) {
            if (TIME_SLOTS.get(index).equals(normalized)) {
                // 业务分支：22:00 为末槽，前端不再自动聚焦
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
}
