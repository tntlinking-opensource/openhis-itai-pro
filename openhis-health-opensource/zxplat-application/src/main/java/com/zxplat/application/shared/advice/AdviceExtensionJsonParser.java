/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import com.zxplat.domain.clinical.procedure.entity.ProcedurePerformer;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.zxplat.framework.core.application.util.StringUtils;

import java.time.LocalDateTime;
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
public final class AdviceExtensionJsonParser {

    /** Jackson 解析器（注册 JavaTime 模块以支持 LocalDateTime）。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    /** 根节点为 JSON 对象时的类型引用。 */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** 根节点为 JSON 数组时的类型引用。 */
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP_TYPE = new TypeReference<>() {};

    /** 执行参与人实体列表的类型引用。 */
    private static final TypeReference<List<ProcedurePerformer>> PERFORMER_LIST_TYPE = new TypeReference<>() {};

    /** 工具类禁止实例化。 */
    private AdviceExtensionJsonParser() {
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
        if (StringUtils.isEmpty(rawJson)) {
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
        if (StringUtils.isEmpty(rawJson)) {
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
        if (StringUtils.isEmpty(rawJson)) {
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

    /**
     * 医嘱/执行单请求扩展快照（由库表 content_json 解析，B.6.2 禁止对外下发原始 JSON 串）。
     */
    @Getter
    @Setter
    @Schema(description = "医嘱请求扩展快照")
    public static class AdviceRequestExtensionSnapshotDto {

        /** 对象形态扩展键值（如停嘱时间等）。 */
        @Schema(description = "对象形态扩展字段")
        private Map<String, Object> attributes = new LinkedHashMap<>();

        /** 数组形态扩展条目（如中药多味明细）。 */
        @Schema(description = "数组形态扩展条目")
        private List<Map<String, Object>> entries = new ArrayList<>();
    }

    /**
     * 执行记录参与人（对齐 {@code cln_procedure_performer}，禁止对外透出 performer_list_json 串）。
     */
    @Getter
    @Setter
    @Schema(description = "执行记录参与人")
    public static class ProcedurePerformerItemDto {

        /** 执业者主键。 */
        @JsonSerialize(using = ToStringSerializer.class)
        @Schema(description = "执业者主键")
        private Long practitionerId;

        /** 参与职能编码（如主刀/助手）。 */
        @Schema(description = "职能编码")
        private String functionCode;

        /** 参与开始时刻。 */
        @Schema(description = "开始时间")
        private LocalDateTime startTime;

        /** 参与结束时刻。 */
        @Schema(description = "结束时间")
        private LocalDateTime endTime;

        /** 来源机构标识。 */
        @Schema(description = "来源机构标识")
        private String orgFromId;

        /** 执业者展示姓名（读侧 enrich 填充）。 */
        @Schema(description = "执业者展示姓名")
        private String practitionerName;
    }

    /**
     * 诊疗套餐子项（对齐 {@code wkf_activity_definition_children} 读模型，禁止对外透出 children_json 串）。
     */
    @Getter
    @Setter
    @Schema(description = "诊疗套餐子项")
    public static class AdvicePackageChildItemDto {

        /** 子项诊疗活动定义主键。 */
        @JsonSerialize(using = ToStringSerializer.class)
        @Schema(description = "子项诊疗项目定义主键")
        private Long childrenId;

        /** 子项数量系数（相对套餐母项）。 */
        @Schema(description = "子项数量系数")
        private Integer childrenNum;

        /** 子项展示名称。 */
        @Schema(description = "子项名称")
        private String childrenName;
    }

    /**
     * 诊疗草稿 {@code childOverridesJson} 中可结构化解析的常用键（执行次数、单位）。
     */
    public static final class ServiceChildOverrideHints {

        /** 无覆盖键时的空实例。 */
        public static final ServiceChildOverrideHints EMPTY = new ServiceChildOverrideHints(null, null);

        /** 计划执行次数（临嘱/门诊护士站上限）。 */
        private final Integer executionCount;
        /** 开立单位 code。 */
        private final String unitCode;

        /**
         * 构造子项覆盖提示。
         *
         * @param executionCount 计划执行次数
         * @param unitCode       开立单位 code
         */
        public ServiceChildOverrideHints(Integer executionCount, String unitCode) {
            this.executionCount = executionCount;
            this.unitCode = unitCode;
        }

        /** @return 计划执行次数 */
        public Integer getExecutionCount() {
            return executionCount;
        }

        /** @return 开立单位 code */
        public String getUnitCode() {
            return unitCode;
        }
    }
}
