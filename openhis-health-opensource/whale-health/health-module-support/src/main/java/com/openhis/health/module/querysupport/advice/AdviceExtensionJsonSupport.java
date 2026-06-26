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

package com.openhis.health.module.querysupport.advice;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openhis.health.module.querysupport.advice.dto.AdvicePackageChildItemDto;
import com.openhis.health.module.querysupport.advice.dto.AdviceRequestExtensionSnapshotDto;
import com.openhis.health.module.querysupport.advice.dto.ProcedurePerformerItemDto;
import com.openhis.health.module.querysupport.advice.dto.ServiceChildOverrideHints;
import com.openhis.health.domain.clinical.procedure.entity.ProcedurePerformer;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 医嘱扩展 JSON 列 → 结构化出参 DTO（B.6.2）。
 *
 * <p>库表与领域层仍可持久化 JSON 快照；应用层 Mapper/Assembler 经本类解析后下发具名字段，
 * 禁止将原始 JSON 串透传到 HTTP Response。</p>
 */
public final class AdviceExtensionJsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP_TYPE = new TypeReference<>() {};

    private static final TypeReference<List<ProcedurePerformer>> PERFORMER_LIST_TYPE = new TypeReference<>() {};

    private AdviceExtensionJsonSupport() {
    }

    /**
     * 解析 {@code content_json} / 医嘱内容快照列为结构化扩展快照。
     *
     * @param rawJson 库表或读模型中的 JSON 原文；空则返回空快照
     * @return 根为数组时填充 {@code entries}，根为对象时填充 {@code attributes}；解析失败时以 {@code rawText} 回落
     */
    public static AdviceRequestExtensionSnapshotDto parseRequestExtensions(String rawJson) {
        AdviceRequestExtensionSnapshotDto snapshot = new AdviceRequestExtensionSnapshotDto();
        // 无快照：调用方按空扩展处理，不下发占位 JSON
        if (StringUtils.isBlank(rawJson)) {
            return snapshot;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson.trim());
            if (root == null || root.isNull()) {
                return snapshot;
            }
            // 中药多味等历史形态：根节点为数组，逐条映射为 entries
            if (root.isArray()) {
                List<Map<String, Object>> entries = OBJECT_MAPPER.convertValue(root, LIST_OF_MAP_TYPE);
                if (entries != null) {
                    snapshot.setEntries(entries);
                }
                return snapshot;
            }
            // 停嘱时间等键值扩展：根节点为对象
            if (root.isObject()) {
                Map<String, Object> attributes = OBJECT_MAPPER.convertValue(root, MAP_TYPE);
                if (attributes != null) {
                    snapshot.setAttributes(attributes);
                }
            }
        } catch (Exception ignored) {
            // 脏数据：保留原文供前端/运维辨认，避免整段丢失
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("rawText", rawJson);
            snapshot.setAttributes(fallback);
        }
        return snapshot;
    }

    /**
     * 解析 {@code performer_list_json} 为执行参与人列表。
     *
     * @param rawJson 执行记录参与人 JSON 数组原文
     * @return 结构化参与人行；空或解析失败时返回空列表（不抛错，避免执行单抽屉整页失败）
     */
    public static List<ProcedurePerformerItemDto> parsePerformers(String rawJson) {
        if (StringUtils.isBlank(rawJson)) {
            return Collections.emptyList();
        }
        try {
            List<ProcedurePerformer> entities = OBJECT_MAPPER.readValue(rawJson, PERFORMER_LIST_TYPE);
            if (entities == null || entities.isEmpty()) {
                return Collections.emptyList();
            }
            List<ProcedurePerformerItemDto> items = new ArrayList<>();
            for (ProcedurePerformer entity : entities) {
                // 跳过数组中的空元素，兼容历史脏数据
                if (entity == null) {
                    continue;
                }
                ProcedurePerformerItemDto dto = new ProcedurePerformerItemDto();
                dto.setPractitionerId(entity.getPractitionerId());
                dto.setFunctionCode(entity.getFunctionCode());
                dto.setStartTime(entity.getStartTime());
                dto.setEndTime(entity.getEndTime());
                dto.setOrgFromId(entity.getOrgFromId());
                items.add(dto);
            }
            return items;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * 解析诊疗草稿 {@code childOverridesJson} 中的执行次数与单位（与前端 {@code advice-medication-draft} 约定一致）。
     *
     * @param rawJson 套餐子项/执行次数覆盖 JSON；空或非法时返回 {@link ServiceChildOverrideHints#EMPTY}
     */
    public static ServiceChildOverrideHints parseServiceChildOverrides(String rawJson) {
        if (StringUtils.isBlank(rawJson)) {
            return ServiceChildOverrideHints.EMPTY;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson.trim());
            if (root == null || !root.isObject()) {
                return ServiceChildOverrideHints.EMPTY;
            }
            Integer executionCount = null;
            if (root.hasNonNull(FieldNameConstants.EXECUTION_COUNT)) {
                executionCount = root.get(FieldNameConstants.EXECUTION_COUNT).asInt();
            }
            String unitCode = null;
            if (root.hasNonNull(FieldNameConstants.UNIT_CODE)) {
                unitCode = trimToNull(root.get(FieldNameConstants.UNIT_CODE).asText());
            }
            return new ServiceChildOverrideHints(executionCount, unitCode);
        } catch (Exception ignored) {
            return ServiceChildOverrideHints.EMPTY;
        }
    }

    /**
     * 活动定义子表读模型行 → 套餐子项出参 DTO。
     *
     * @param model 子表查询模型；{@code null} 时返回 {@code null}
     * @return 套餐子项；无对应子项时 {@code null}
     */
    public static AdvicePackageChildItemDto toPackageChildItem(ActivityDefinitionChildQueryModel model) {
        if (model == null) {
            return null;
        }
        AdvicePackageChildItemDto dto = new AdvicePackageChildItemDto();
        dto.setChildrenId(model.getChildrenId());
        dto.setChildrenNum(model.getChildrenNum());
        dto.setChildrenName(model.getChildrenName());
        return dto;
    }

    /**
     * 批量将活动定义子表行转为套餐子项列表（医生站医嘱列表 B.6.2）。
     *
     * @param models 按活动定义主键批量加载的子项；{@code null} 或空时返回空列表
     * @return 非 {@code null} 的子项 DTO 列表，顺序与子表查询一致
     */
    public static List<AdvicePackageChildItemDto> toPackageChildItems(List<ActivityDefinitionChildQueryModel> models) {
        if (models == null || models.isEmpty()) {
            return Collections.emptyList();
        }
        List<AdvicePackageChildItemDto> items = new ArrayList<>();
        for (ActivityDefinitionChildQueryModel model : models) {
            AdvicePackageChildItemDto dto = toPackageChildItem(model);
            if (dto != null) {
                items.add(dto);
            }
        }
        return items;
    }
}