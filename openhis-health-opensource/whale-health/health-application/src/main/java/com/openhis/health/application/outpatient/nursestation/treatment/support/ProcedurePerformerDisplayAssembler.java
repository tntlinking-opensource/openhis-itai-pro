/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.treatment.support;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.application.shared.advice.AdviceExtensionJsonParser.ProcedurePerformerItemDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 执行参与人展示名回填：批量解析 {@code adm_practitioner.name}，供护士站列表与执行单读侧使用。
 */
@Component
@RequiredArgsConstructor
public class ProcedurePerformerDisplayAssembler {

    private final PractitionerQueryService practitionerQueryService;

    /**
     * 为参与人列表批量回填 {@code practitionerName}。
     *
     * @param items 参与人 DTO 列表；允许 null 或空
     */
    public void enrichPerformerItemDisplayNames(List<ProcedurePerformerItemDto> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        for (ProcedurePerformerItemDto item : items) {
            if (item != null && item.getPractitionerId() != null) {
                practitionerIds.add(item.getPractitionerId());
            }
        }
        if (practitionerIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameByPractitionerId = this.buildNameByPractitionerId(practitionerIds);
        for (ProcedurePerformerItemDto item : items) {
            if (item == null || item.getPractitionerId() == null) {
                continue;
            }
            String displayName = nameByPractitionerId.get(item.getPractitionerId());
            if (StringUtils.isNotEmpty(displayName)) {
                item.setPractitionerName(displayName);
            }
        }
    }

    /**
     * 解析主执行人展示名（取列表中首个已回填姓名的参与人）。
     *
     * @param items 参与人 DTO 列表；允许 null 或空
     * @return 展示名；无命中时返回 null
     */
    public String resolvePrimaryPerformerDisplayName(List<ProcedurePerformerItemDto> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        this.enrichPerformerItemDisplayNames(items);
        for (ProcedurePerformerItemDto item : items) {
            if (item != null && StringUtils.isNotEmpty(item.getPractitionerName())) {
                return item.getPractitionerName();
            }
        }
        return null;
    }

    /**
     * 按医务人员主键批量解析展示名。
     *
     * @param practitionerIds 医务人员主键集合
     * @return practitionerId → 姓名
     */
    public Map<Long, String> mapDisplayNameByPractitionerIds(Collection<Long> practitionerIds) {
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return Map.of();
        }
        return this.buildNameByPractitionerId(practitionerIds);
    }

    /**
     * 批量加载医务人员姓名映射。
     *
     * @param practitionerIds 医务人员主键集合
     * @return practitionerId → 姓名
     */
    private Map<Long, String> buildNameByPractitionerId(Collection<Long> practitionerIds) {
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long practitionerId : practitionerIds) {
            if (practitionerId != null) {
                distinct.add(practitionerId);
            }
        }
        if (distinct.isEmpty()) {
            return Map.of();
        }
        List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(new ArrayList<>(distinct));
        Map<Long, String> nameByPractitionerId = new HashMap<>(distinct.size());
        for (Practitioner practitioner : practitioners) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(practitioner.getName())) {
                nameByPractitionerId.put(practitioner.getId(), practitioner.getName());
            }
        }
        return nameByPractitionerId;
    }
}
