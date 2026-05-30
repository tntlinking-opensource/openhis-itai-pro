package com.openhis.health.application.mrms.adapter;

import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionSearchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.whale.mrms.domain.port.IcdSearchPort;
import com.openhis.whale.mrms.domain.port.model.IcdItemSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * health 对 MRMS {@link IcdSearchPort} 的实现（ICD-10 走诊断定义目录）。
 */
@Component
public class HealthMrmsIcdSearchAdapter implements IcdSearchPort {

    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    public HealthMrmsIcdSearchAdapter(ConditionDefinitionQueryService conditionDefinitionQueryService) {
        this.conditionDefinitionQueryService = conditionDefinitionQueryService;
    }

    @Override
    public List<IcdItemSnapshot> searchIcd10(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        List<ConditionDefinitionSearchItemQueryModel> items =
                conditionDefinitionQueryService.getConditionDefinitionListBySearchKey(keyword.trim());
        return items.stream()
                .limit(Math.max(limit, 1))
                .map(this::mapItem)
                .collect(Collectors.toList());
    }

    @Override
    public List<IcdItemSnapshot> searchIcd9Cm3(String keyword, int limit) {
        // Wave 1：ICD-9-CM3 目录尚未统一，返回空
        return Collections.emptyList();
    }

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
