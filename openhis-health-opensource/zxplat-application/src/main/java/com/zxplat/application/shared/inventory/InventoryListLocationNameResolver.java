/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.inventory;

import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 库存单据列表/选单：批量解析库位展示名（药库/药房/科室药房）。
 *
 * <p>供各 inventory Feature AppService 列表与 {@code pageLinkable*} 选单 enrich 复用；
 * 本类仅依赖 Location 聚合读口（B.6.4），<strong>禁止</strong>依赖任何 Feature Request/Response/DTO。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryListLocationNameResolver {

    private final LocationQueryService locationQueryService;

    /**
     * 按库位主键批量解析非空名称（去重后逐条查询；页大小有上界）。
     *
     * @param locationIds 源/目的库位主键集合，可含 null
     * @return 主键 → trim 后名称；未命中或不在册者不入 Map
     */
    public Map<Long, String> resolveNamesByIds(Collection<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> distinct = new HashSet<>();
        for (Long id : locationIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> nameById = new HashMap<>();
        for (Long locationId : distinct) {
            // B.6.4：跨聚合读 LocationQueryService，仅取展示名
            Optional<Location> locOpt = this.locationQueryService.findOptionalById(locationId);
            if (locOpt.isEmpty()) {
                continue;
            }
            Location loc = locOpt.get();
            if (loc != null && StringUtils.isNotEmpty(loc.getName())) {
                nameById.put(locationId, loc.getName().trim());
            }
        }
        return nameById;
    }
}
