/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inventory;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 库存单据列表侧栏：批量解析医务人员展示名（制单人/审批人）。
 *
 * <p>供各 inventory Feature AppService 列表 enrich 复用；本类仅依赖 Practitioner 聚合读口（B.6.4），
 * <strong>禁止</strong>依赖任何 Feature Request/Response/DTO。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryListPractitionerNameResolver {

    private final PractitionerQueryService practitionerQueryService;

    /**
     * 按医务人员主键批量解析非空姓名（去重后逐条查询；页大小有上界）。
     *
     * @param practitionerIds 申请人/审批人等主键集合，可含 null
     * @return 主键 → trim 后姓名；未命中或不在册者不入 Map
     */
    public Map<Long, String> resolveNamesByIds(Collection<Long> practitionerIds) {
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> distinct = new HashSet<>();
        for (Long id : practitionerIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> nameById = new HashMap<>();
        for (Long pid : distinct) {
            // B.6.4：跨聚合读 PractitionerQueryService，仅取展示名
            Practitioner practitioner = this.practitionerQueryService.findById(pid);
            if (practitioner != null && StringUtils.isNotEmpty(practitioner.getName())) {
                nameById.put(pid, practitioner.getName().trim());
            }
        }
        return nameById;
    }
}
