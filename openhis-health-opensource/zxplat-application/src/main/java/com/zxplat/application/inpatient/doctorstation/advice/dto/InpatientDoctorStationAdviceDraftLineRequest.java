/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import com.zxplat.domain.share.constants.DoctorStationAdviceConstants;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-14：暂存医嘱批次中的单行指令（CREATE / UPDATE / DELETE）。
 *
 * <p>通过 {@link #productTableName} 区分药品与诊疗载荷；住院场景<strong>不含耗材</strong>（BR-IP-ORD-012）。
 * {@link #therapyTypeCode} 驱动长/临/出院带药分轨：住院三种 therapy 暂存均仅写 Request（BR-IP-ORD-006/029）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站医嘱暂存批次行")
public class InpatientDoctorStationAdviceDraftLineRequest {

    /** 行操作：CREATE / UPDATE / DELETE。 */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_LINE_OP_REQUIRED)
    @Schema(description = "行操作：CREATE / UPDATE / DELETE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lineOp;

    /** 锚定产品定义物理表名，与目录、列表、签发契约一致。 */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_PRODUCT_TABLE_NAME_REQUIRED)
    @Schema(
        description = "产品定义表名（与 IF-IP-DOR-11 一致）：med_medication_definition / wkf_activity_definition",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String productTableName;

    /** 药品/诊疗/耗材申请聚合主键；CREATE 时通常为空由领域分配。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 请求主键；UPDATE/DELETE 必填。 */
    @Schema(description = "请求主键；UPDATE/DELETE 必填")
    private Long requestId;

    /** 医生站列表展示排序号（可选，小靠前）。 */
    @Schema(description = "医生站列表展示排序号（可选，小靠前）")
    private Integer sortNumber;

    /** 药品载荷。 */
    @Schema(description = "药品载荷")
    private SaveInpatientMedicationAdviceDraftLinePayload medicationPayload;

    /** 诊疗载荷。 */
    @Schema(description = "诊疗载荷")
    private SaveInpatientServiceAdviceDraftLinePayload servicePayload;

    /**
     * 治疗类型：{@link com.zxplat.domain.share.enums.TherapyType#getCode()}
     * （{@code LONG_TERM} / {@code TEMPORARY} / {@code DISCHARGE_MEDICATION}）。
     */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_THERAPY_TYPE_REQUIRED)
    @Schema(
        description = "治疗类型：LONG_TERM / TEMPORARY / DISCHARGE_MEDICATION",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String therapyTypeCode;
}
