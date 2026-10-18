/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.summarydispense.support;

import com.zxplat.application.shared.encounter.EncounterContextReader;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 汇总领药单「申领科室」解析（护士站 IF-IP-SUM-03 / 药房 IF-IPS-02 打印同源）。
 *
 * <p>口径：发放行就诊所属<strong>病区</strong>的主挂靠科室（{@code Location.organizationId}）；
 * 病区未挂科室时回落就诊当前科室名。多病区多科室时按出现顺序去重后以顿号拼接。</p>
 *
 * <p>加载必须走 {@link EncounterContextReader#listInpatientContextsByEncounterIdsOrdered}，
 * <strong>禁止</strong>走带会话组织/管辖病区收窄的 {@code listInpatientEncounterContexts}，
 * 否则药房打印或跨管辖场景会得到空上下文，申领科室无法回显。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicationSummaryApplicantDepartmentResolver {

    /** 住院就诊上下文（按主键直查，含病区 / 当前科室）。 */
    private final EncounterContextReader encounterContextReader;
    /** 病区位置实体（主挂靠科室）。 */
    private final LocationQueryService locationQueryService;
    /** 科室名称批量解析。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * 按汇总单内就诊集合解析申领科室展示名。
     *
     * @param encounterIds 发放行上的就诊主键；空则返回空串
     * @return 申领科室名；无法解析时为空串（打印模板留空）
     */
    public String resolveByEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return "";
        }
        LinkedHashSet<Long> distinctEncounterIds = new LinkedHashSet<>();
        for (Long encounterId : encounterIds) {
            // 跳过空主键，避免无效批量查询
            if (encounterId != null) {
                distinctEncounterIds.add(encounterId);
            }
        }
        if (distinctEncounterIds.isEmpty()) {
            return "";
        }
        // 按就诊主键直查：不受会话组织/管辖病区过滤，药房与护士站打印同口径
        List<InpatientEncounterContextQueryModel> contexts =
            encounterContextReader.listInpatientContextsByEncounterIdsOrdered(
                new ArrayList<>(distinctEncounterIds));
        if (contexts == null || contexts.isEmpty()) {
            return "";
        }
        LinkedHashSet<Long> wardIds = new LinkedHashSet<>();
        LinkedHashSet<String> fallbackOrgNames = new LinkedHashSet<>();
        for (InpatientEncounterContextQueryModel context : contexts) {
            if (context == null) {
                continue;
            }
            // 优先收集病区，后续用病区主挂靠科室作申领科室
            if (context.getWardId() != null) {
                wardIds.add(context.getWardId());
            }
            // 病区缺失或未挂科室时，回落就诊当前科室名
            if (StringUtils.isNotEmpty(context.getOrganizationName())) {
                fallbackOrgNames.add(context.getOrganizationName().trim());
            }
        }
        LinkedHashSet<String> departmentNames = new LinkedHashSet<>();
        if (!wardIds.isEmpty()) {
            Map<Long, Location> wardById = locationQueryService.loadLocationEntityMapByIds(wardIds);
            LinkedHashSet<Long> organizationIds = new LinkedHashSet<>();
            for (Long wardId : wardIds) {
                Location ward = wardById.get(wardId);
                // 病区主挂靠科室为空则跳过，留给回落路径
                if (ward == null || ward.getOrganizationId() == null) {
                    continue;
                }
                organizationIds.add(ward.getOrganizationId());
            }
            if (!organizationIds.isEmpty()) {
                Map<Long, String> nameById = organizationQueryService.mapNamesByIds(organizationIds);
                for (Long organizationId : organizationIds) {
                    String name = nameById.get(organizationId);
                    // 名称缺失不入集，避免打印出空白顿号段
                    if (StringUtils.isNotEmpty(name)) {
                        departmentNames.add(name.trim());
                    }
                }
            }
        }
        // 病区主挂靠未能解析出任何科室名时，回落就诊当前科室
        if (departmentNames.isEmpty()) {
            departmentNames.addAll(fallbackOrgNames);
        }
        if (departmentNames.isEmpty()) {
            return "";
        }
        return String.join("、", departmentNames);
    }
}
