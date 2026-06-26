/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms.reader;

import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionSearchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.queries.ActivityDefinitionPageQuery;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionPageQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.mrms.contracts.reader.HealthIcdDictionaryReader;
import com.openhis.whale.mrms.contracts.snapshot.IcdItemSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * {@link HealthIcdDictionaryReader} 实现：ICD-10 对接诊断定义目录；ICD-9-CM-3 对接手术类诊疗项目目录。
 */
@Component
@RequiredArgsConstructor
public class HealthIcdDictionaryReaderAdapter implements HealthIcdDictionaryReader {

    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    @Override
    public List<IcdItemSnapshot> searchIcd10(String keyword, int limit) {
        String key = trimToNull(keyword);
        if (key == null) {
            return Collections.emptyList();
        }
        List<ConditionDefinitionSearchItemQueryModel> items =
                conditionDefinitionQueryService.getConditionDefinitionListBySearchKey(key);
        return toIcd10Snapshots(items, resolveLimit(limit));
    }

    @Override
    public List<IcdItemSnapshot> searchIcd9Cm3(String keyword, int limit) {
        String key = trimToNull(keyword);
        if (key == null) {
            return Collections.emptyList();
        }
        int resolvedLimit = resolveLimit(limit);
        ActivityDefinitionPageQuery query = new ActivityDefinitionPageQuery();
        query.setCategoryCode(ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE);
        query.setKeyword(key);
        query.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        Page<ActivityDefinitionPageQueryModel> page = activityDefinitionQueryService.pageServiceCatalog(
                query,
                PageRequest.of(0, resolvedLimit));
        if (page.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> activityIds = new LinkedHashSet<>();
        for (ActivityDefinitionPageQueryModel row : page.getContent()) {
            if (row != null && row.getId() != null) {
                activityIds.add(row.getId());
            }
        }
        Map<Long, ActivityDefinition> activityById = new HashMap<>();
        for (ActivityDefinition activity : activityDefinitionQueryService.findAllByIds(activityIds)) {
            if (activity != null && activity.getId() != null) {
                activityById.put(activity.getId(), activity);
            }
        }
        List<IcdItemSnapshot> result = new ArrayList<>();
        for (ActivityDefinitionPageQueryModel row : page.getContent()) {
            if (result.size() >= resolvedLimit || row == null || row.getId() == null) {
                break;
            }
            ActivityDefinition activity = activityById.get(row.getId());
            if (activity == null) {
                continue;
            }
            String code = firstNonBlank(trimToNull(activity.getYbNo()), trimToNull(activity.getBusNo()));
            String name = trimToNull(activity.getName());
            if (code == null && name == null) {
                continue;
            }
            IcdItemSnapshot snap = new IcdItemSnapshot();
            snap.setCode(code);
            snap.setName(name);
            snap.setSearchCode(trimToNull(activity.getPyStr()));
            result.add(snap);
        }
        return result;
    }

    private int resolveLimit(int limit) {
        if (limit < 1) {
            return 20;
        }
        if (limit > 100) {
            return 100;
        }
        return limit;
    }

    private List<IcdItemSnapshot> toIcd10Snapshots(
            List<ConditionDefinitionSearchItemQueryModel> items, int limit) {
        List<IcdItemSnapshot> result = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return result;
        }
        for (ConditionDefinitionSearchItemQueryModel item : items) {
            if (result.size() >= limit) {
                break;
            }
            IcdItemSnapshot snap = new IcdItemSnapshot();
            snap.setCode(item.getConditionCode());
            snap.setName(item.getName());
            snap.setSearchCode(item.getPyStr());
            result.add(snap);
        }
        return result;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }
}
