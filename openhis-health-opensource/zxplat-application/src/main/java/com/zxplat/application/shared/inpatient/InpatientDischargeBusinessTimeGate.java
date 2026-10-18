/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.inpatient;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 住院业务时刻 vs 计划出院时间门禁（划价写路径双保险）。
 *
 * <p>真源同 {@link BusinessDischargeTimeResolver}：生效态出院嘱计划时间优先。
 * 无计划出院时间时放行（尚未开立/校对出院嘱）；有则业务时刻不得严格晚于出院时间。
 * 前端有提示但缺 {@code dischargeAt} 会跳过，本 Gate 为后端硬门禁（**BR-IP-PRC-035**）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientDischargeBusinessTimeGate {

    /** 出院时间展示格式。 */
    private static final DateTimeFormatter DISCHARGE_DISPLAY =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 业务出院时间解析（计划出院优先）。 */
    private final BusinessDischargeTimeResolver businessDischargeTimeResolver;

    /**
     * 断言单就诊业务时刻不晚于计划出院时间。
     *
     * @param encounterId  就诊主键；空则跳过
     * @param businessTime 划价/改时等业务时刻；空则跳过
     * @param errorCode    业务异常码（如 EX-IP-PRC-206）
     * @param actionLabel  动作文案（如「划价」→「划价时间不能晚于出院时间」）
     * @throws BusinessException 业务时刻严格晚于计划出院时
     */
    public void assertNotAfterPlannedDischarge(
        Long encounterId,
        LocalDateTime businessTime,
        String errorCode,
        String actionLabel) {
        // Gate：缺就诊或业务时刻则无法比对，放行（由调用方保证必填）
        if (encounterId == null || businessTime == null) {
            return;
        }
        // Pre：解析计划出院；无生效出院嘱则尚未进入出院时间窗，放行
        LocalDateTime plannedDischarge = businessDischargeTimeResolver.resolve(encounterId, null);
        if (plannedDischarge == null) {
            return;
        }
        // Gate：严格晚于出院时间则拒绝（等于允许）
        if (!businessTime.isAfter(plannedDischarge)) {
            return;
        }
        // Asm：缺动作文案时回落「业务」，拼出用户可读拒绝原因
        String label = actionLabel;
        if (StringUtils.isEmpty(label)) {
            label = "业务";
        }
        throw new BusinessException(
            errorCode,
            label + "时间不能晚于出院时间（" + plannedDischarge.format(DISCHARGE_DISPLAY) + "）");
    }

    /**
     * 批量断言：各就诊业务时刻均不晚于各自计划出院时间（同批多患者）。
     *
     * @param encounterIds 就诊主键集合
     * @param businessTime 整批共用业务时刻
     * @param errorCode    业务异常码
     * @param actionLabel  动作文案
     * @throws BusinessException 任一就诊越界时
     */
    public void assertNotAfterPlannedDischargeForEncounters(
        Collection<Long> encounterIds,
        LocalDateTime businessTime,
        String errorCode,
        String actionLabel) {
        // Gate：无就诊或无业务时刻则跳过
        if (encounterIds == null || encounterIds.isEmpty() || businessTime == null) {
            return;
        }
        Set<Long> distinct = new HashSet<>();
        for (Long encounterId : encounterIds) {
            if (encounterId == null) {
                continue;
            }
            // 同批去重，避免重复解析
            if (!distinct.add(encounterId)) {
                continue;
            }
            this.assertNotAfterPlannedDischarge(encounterId, businessTime, errorCode, actionLabel);
        }
    }
}
