/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.coding.service;

import com.openhis.health.application.mrms.coding.dto.IcdItemDto;
import com.openhis.health.application.mrms.coding.request.SearchIcdRequest;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionSearchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.queries.ActivityDefinitionPageQuery;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionPageQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页 ICD 选码应用服务（IF-MR-Coding）。
 *
 * <p>路由前缀 {@code POST /api/app/mrmsCaseRecordCoding/*}。为首页诊断/手术 picker 提供
 * ICD-10 疾病与 ICD-9-CM-3 手术编码模糊检索，只读不写库。</p>
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordCodingAppService {

    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /**
     * 按关键字检索 ICD-10 疾病编码（首页诊断 picker）。
     *
     * @param request 关键字与条数上限
     * @return 编码项列表；关键字为空时返回空列表
     */
    public List<IcdItemDto> searchIcd10(SearchIcdRequest request) {
        // Gate：无关键字时不查库，避免全表扫描
        String key = trimToNull(request.getKeyword());
        if (key == null) {
            return Collections.emptyList();
        }
        int limit = resolveLimit(request.getLimit());
        // Pre：经临床疾病定义 QS 模糊检索
        List<ConditionDefinitionSearchItemQueryModel> items =
                conditionDefinitionQueryService.getConditionDefinitionListBySearchKey(key);
        // Asm：截断并映射 picker 行
        return toIcd10Dtos(items, limit);
    }

    /**
     * 按关键字检索 ICD-9-CM-3 手术编码（首页手术 picker）。
     *
     * @param request 关键字与条数上限
     * @return 编码项列表；关键字为空时返回空列表
     */
    public List<IcdItemDto> searchIcd9Cm3(SearchIcdRequest request) {
        // Gate：无关键字时不查库
        String key = trimToNull(request.getKeyword());
        if (key == null) {
            return Collections.emptyList();
        }
        int limit = resolveLimit(request.getLimit());
        // Pre：分页查 ACTIVE 手术类活动定义
        ActivityDefinitionPageQuery query = new ActivityDefinitionPageQuery();
        query.setCategoryCode(ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE);
        query.setKeyword(key);
        query.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        Page<ActivityDefinitionPageQueryModel> page = activityDefinitionQueryService.pageServiceCatalog(
                query,
                PageRequest.of(0, limit));
        if (page.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量加载实体以取医保码/拼音（分页模型字段不全）
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
        // Asm：映射 picker 行，优先医保编码
        List<IcdItemDto> result = new ArrayList<>();
        for (ActivityDefinitionPageQueryModel row : page.getContent()) {
            if (result.size() >= limit || row == null || row.getId() == null) {
                break;
            }
            ActivityDefinition activity = activityById.get(row.getId());
            // 实体被删或未加载时跳过
            if (activity == null) {
                continue;
            }
            String code = firstNonBlank(trimToNull(activity.getYbNo()), trimToNull(activity.getBusNo()));
            String name = trimToNull(activity.getName());
            // 编码与名称均为空则跳过，避免前端展示空行
            if (code == null && name == null) {
                continue;
            }
            IcdItemDto dto = new IcdItemDto();
            dto.setCode(code);
            dto.setName(name);
            dto.setPyStr(trimToNull(activity.getPyStr()));
            result.add(dto);
        }
        return result;
    }

    /**
     * 解析条数上限：默认 20，最大 100。
     *
     * @param limit 请求条数，可为 null
     * @return 合法上限
     */
    private int resolveLimit(Integer limit) {
        // 默认 20 条
        if (limit == null || limit < 1) {
            return 20;
        }
        // 上限 100，防止 picker 一次拉取过多
        if (limit > 100) {
            return 100;
        }
        return limit;
    }

    /**
     * ICD-10 检索结果转 picker DTO，按 limit 截断。
     *
     * @param items 疾病定义检索行
     * @param limit 最大条数
     * @return picker 行列表
     */
    private List<IcdItemDto> toIcd10Dtos(List<ConditionDefinitionSearchItemQueryModel> items, int limit) {
        List<IcdItemDto> result = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return result;
        }
        for (ConditionDefinitionSearchItemQueryModel item : items) {
            if (result.size() >= limit) {
                break;
            }
            IcdItemDto dto = new IcdItemDto();
            dto.setCode(item.getConditionCode());
            dto.setName(item.getName());
            dto.setPyStr(item.getPyStr());
            result.add(dto);
        }
        return result;
    }

    /**
     * 返回第一个非空白字符串。
     *
     * @param values 候选值
     * @return 首个非 null  trimmed 值，或 null
     */
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
