/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-IP-DOR-14：诊疗医嘱草稿行载荷（嵌于 {@link InpatientDoctorStationAdviceDraftLineRequest#getServicePayload()}）。
 *
 * <p>锚定 {@code wkf_activity_definition}。嘱托行须填 {@link #entrustContent}
 *（BR-IP-ORD-028）；套餐子项可由 {@link #childOverridesJson} 覆盖活动定义默认 children_json。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站诊疗医嘱草稿行载荷")
public class SaveInpatientServiceAdviceDraftLinePayload {

    /** {@code wkf_activity_definition} 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊疗活动定义主键")
    private Long activityDefinitionId;

    /** 开立数量（账单计价乘数；长嘱为单次执行量，与药品 quantity 语义一致）。 */
    @Schema(description = "开立数量（账单计价）")
    private BigDecimal quantity;

    /** 开立单位术语编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 计划执行次数（临嘱/门诊护士站拆解；长嘱按频次执行时可为空）。 */
    @Schema(description = "计划执行次数")
    private Integer executionCount;

    /** 默认执行科室（组织单元，落库 {@code wkf_service_request.org_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行科室")
    private Long performOrgId;

    /** 套餐展开子项覆盖 JSON，结构由前端与领域约定。 */
    @Schema(description = "套餐子项覆盖 JSON")
    private String childOverridesJson;

    /**
     * 绑定就诊诊断主键有序串（落库 {@code wkf_service_request.encounter_diagnosis_ids}；有序，首位为主诊断）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "绑定就诊诊断主键有序串")
    private String encounterDiagnosisIds;

    /** 频次编码（长期诊疗必填；落库 {@code wkf_service_request.rate_code}）。 */
    @Schema(description = "频次编码（长期诊疗必填）")
    private String rateCode;

    /**
     * 嘱托医嘱正文（落库 {@code wkf_service_request.entrust_content}）。
     * 嘱托行（类目 52 / 目录名「嘱托」）必填；常规诊疗可选备注嘱托（AS-IP-DOR-003 · BR-IP-ORD-028）。
     */
    @Schema(description = "嘱托正文（嘱托行必填）")
    private String entrustContent;

    /**
     * 医嘱备注（展示用；落库 {@code wkf_service_request.remark}）。
     * <p>与 {@link #entrustContent} 分立；当 {@link #childOverridesJson} 非空时，remark 列优先承载套餐子项覆盖，
     * 本字段不写入（避免与历史 note=childOverridesJson 口径冲突）。</p>
     */
    @Schema(description = "医嘱备注（无套餐子项覆盖时写入）")
    private String remark;

    /** 费用性质/合同覆盖，可空表示走患者默认。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质覆盖")
    private Long contractId;
}
