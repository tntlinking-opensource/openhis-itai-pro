/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.integration.winning.catalog.orchestration;

import com.openhis.health.application.integration.winning.catalog.mapper.WinningCatalogSyncMapper;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 同步后按 busNo 批量解析定义主键，供定价刷新使用。
 */
@Component
@RequiredArgsConstructor
public class WinningCatalogInstanceIdResolver {

    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final WinningCatalogSyncMapper mapper;

    public Map<String, Long> resolveDeviceIds(List<String> busNos) {
        if (busNos.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> map = new HashMap<>();
        for (DeviceDefinition row : deviceDefinitionQueryService.findByBusNos(busNos)) {
            map.put(row.getBusNo(), row.getId());
        }
        return map;
    }

    public Map<String, Long> resolveActivityIds(List<String> busNos) {
        if (busNos.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> map = new HashMap<>();
        for (ActivityDefinition row : activityDefinitionQueryService.findByBusNos(busNos)) {
            map.put(row.getBusNo(), row.getId());
        }
        return map;
    }

    public <T> List<String> collectBusNos(List<T> items, Function<T, String> busNoFn, Function<T, String> externalFn) {
        List<String> busNos = new ArrayList<>();
        for (T item : items) {
            String busNo = mapper.resolveBusNo(busNoFn.apply(item), externalFn.apply(item));
            if (busNo != null) {
                busNos.add(busNo);
            }
        }
        return busNos;
    }
}
