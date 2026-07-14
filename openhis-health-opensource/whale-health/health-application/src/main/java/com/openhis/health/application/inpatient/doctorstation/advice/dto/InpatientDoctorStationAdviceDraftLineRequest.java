/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-14：暂存医嘱批次中的单行指令（CREATE / UPDATE / DELETE）。
 *
 * <p>通过 {@link #productTableName} 区分药品与诊疗载荷；住院场景<strong>不含耗材</strong>（BR-IP-ORD-012）。
 * {@link #therapyTypeCode} 驱动长临分轨：长期药品仅写 Request；临时药品/诊疗与门诊一致（暂存费用草稿，签发建发放）。</p>
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

    /** 长临医嘱类型：{@link com.openhis.health.domain.share.enums.TherapyType#getCode()}。 */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_THERAPY_TYPE_REQUIRED)
    /** 长临医嘱：LONG_TERM / TEMPORARY。 */
    @Schema(description = "长临医嘱：LONG_TERM / TEMPORARY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String therapyTypeCode;
}
