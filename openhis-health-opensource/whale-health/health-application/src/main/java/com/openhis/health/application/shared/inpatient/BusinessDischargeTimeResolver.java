/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inpatient;

import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 业务出院时间解析（打印/抬头/列表/报表共用）。
 *
 * <p>真源：出院类医嘱 {@code occurrence_start_time}（医生填写的计划出院时间）；
 * 跳过已取消嘱；回落 {@code Encounter.endTime}（护士办理出院写入）。
 * 禁止用清床/结算时刻冒充出院时间。</p>
 */
@Component
@RequiredArgsConstructor
public class BusinessDischargeTimeResolver {

    /** 服务申请读侧（出院嘱计划时间批量解析）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /**
     * 解析单就诊业务出院时间。
     *
     * @param encounterId      就诊主键；空则仅回落 endTime
     * @param encounterEndTime 就诊 end_time（办理出院写入）；可空
     * @return 计划出院时间或 endTime；皆空则 null
     */
    public LocalDateTime resolve(Long encounterId, LocalDateTime encounterEndTime) {
        if (encounterId == null) {
            return encounterEndTime;
        }
        Map<Long, LocalDateTime> planned =
            serviceRequestQueryService.mapDischargePlannedTimeByEncounterIds(List.of(encounterId));
        LocalDateTime fromAdvice = planned.get(encounterId);
        if (fromAdvice != null) {
            return fromAdvice;
        }
        return encounterEndTime;
    }

    /**
     * 批量解析业务出院时间（列表/报表一页一次 IN，禁止循环单条查）。
     *
     * @param encounterIds         本页就诊主键
     * @param encounterEndTimeById 回落用 end_time（可空 Map）；键为 encounterId
     * @return encounterId → 业务出院时间；无计划时间且无 end 时不含该键
     */
    public Map<Long, LocalDateTime> mapByEncounterIds(
        Collection<Long> encounterIds,
        Map<Long, LocalDateTime> encounterEndTimeById) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                distinctIds.add(encounterId);
            }
        }
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, LocalDateTime> endFallback = encounterEndTimeById;
        if (endFallback == null) {
            endFallback = Collections.emptyMap();
        }
        // Pre：领域批量筛出院嘱计划时间
        Map<Long, LocalDateTime> fromAdviceByEncounter =
            serviceRequestQueryService.mapDischargePlannedTimeByEncounterIds(distinctIds);
        Map<Long, LocalDateTime> result = new HashMap<>(distinctIds.size());
        for (Long encounterId : distinctIds) {
            LocalDateTime fromAdvice = fromAdviceByEncounter.get(encounterId);
            if (fromAdvice != null) {
                result.put(encounterId, fromAdvice);
                continue;
            }
            LocalDateTime endTime = endFallback.get(encounterId);
            if (endTime != null) {
                result.put(encounterId, endTime);
            }
        }
        return result;
    }
}
