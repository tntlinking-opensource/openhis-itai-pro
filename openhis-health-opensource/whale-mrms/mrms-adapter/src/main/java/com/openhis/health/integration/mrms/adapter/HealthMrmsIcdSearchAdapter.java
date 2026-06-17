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

package com.openhis.health.integration.mrms.adapter;

import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionSearchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.whale.mrms.port.IcdSearchPort;
import com.openhis.whale.mrms.port.model.IcdItemSnapshot;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * health 对 MRMS {@link IcdSearchPort} 的实现（ICD-10 走诊断定义目录）。
 */
@Component
@Primary
public class HealthMrmsIcdSearchAdapter implements IcdSearchPort {

    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    /**
     * @param conditionDefinitionQueryService 诊断定义查询
     */
    public HealthMrmsIcdSearchAdapter(ConditionDefinitionQueryService conditionDefinitionQueryService) {
        this.conditionDefinitionQueryService = conditionDefinitionQueryService;
    }

    /**
     * 按关键字检索 ICD-10 诊断目录。
     *
     * @param keyword 检索关键字
     * @param limit   最大条数
     * @return ICD 项列表；关键字空白时返回空列表
     */
    @Override
    public List<IcdItemSnapshot> searchIcd10(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        List<ConditionDefinitionSearchItemQueryModel> items =
            conditionDefinitionQueryService.getConditionDefinitionListBySearchKey(keyword.trim());
        int maxRows = limit;
        if (maxRows < 1) {
            maxRows = 1;
        }
        List<IcdItemSnapshot> result = new ArrayList<>();
        for (ConditionDefinitionSearchItemQueryModel item : items) {
            if (result.size() >= maxRows) {
                break;
            }
            result.add(mapItem(item));
        }
        return result;
    }

    /**
     * ICD-9-CM3 检索（Wave 1 尚未对接，返回空列表）。
     *
     * @param keyword 检索关键字
     * @param limit   最大条数
     * @return 空列表
     */
    @Override
    public List<IcdItemSnapshot> searchIcd9Cm3(String keyword, int limit) {
        return Collections.emptyList();
    }

    /**
     * 将诊断定义检索项映射为 ICD 快照。
     *
     * @param item 诊断定义检索项
     * @return ICD 快照
     */
    private IcdItemSnapshot mapItem(ConditionDefinitionSearchItemQueryModel item) {
        IcdItemSnapshot snapshot = new IcdItemSnapshot();
        snapshot.setId(item.getId());
        snapshot.setCode(item.getConditionCode());
        snapshot.setName(item.getName());
        snapshot.setPyStr(item.getPyStr());
        snapshot.setWbStr(item.getWbStr());
        return snapshot;
    }
}
