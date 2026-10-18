/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-DOR-14：诊疗医嘱草稿行载荷。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站诊疗医嘱草稿行载荷")
public class SaveServiceAdviceDraftLinePayload {

    /** {@code wkf_activity_definition} 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊疗活动定义主键")
    private Long activityDefinitionId;

    /** 开立数量（账单计价乘数，与 {@link #unitCode} 同语义）。 */
    @Schema(description = "开立数量（账单计价）")
    private BigDecimal quantity;

    /** 开立单位术语编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /**
     * 医嘱优先级 code（DM-WKF-004；检验/检查加急为 {@code EM}；空表示非急）。
     */
    @Schema(description = "医嘱优先级 code（加急=EM）")
    private String priorityEnum;

    /** 计划执行次数（护士站拆解可执行上限；门诊/临嘱必填）。 */
    @Schema(description = "计划执行次数")
    private Integer executionCount;

    /** 默认执行科室（组织单元，落库 {@code wkf_service_request.org_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 执行科室。 */
    @Schema(description = "执行科室")
    private Long performOrgId;

    /** 套餐展开子项覆盖 JSON，结构由前端与领域约定。 */
    @Schema(description = "套餐子项覆盖 JSON")
    private String childOverridesJson;

    /**
     * 绑定就诊诊断主键有序串（落库 {@code wkf_service_request.encounter_diagnosis_ids}；有序，首位为主诊断）。
     */
    @Schema(description = "绑定就诊诊断主键有序串")
    private String encounterDiagnosisIds;

    /** 费用性质/合同覆盖，可空表示走患者默认。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质覆盖")
    private Long contractId;
}
