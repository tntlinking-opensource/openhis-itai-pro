/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-14：暂存批次中的单行指令。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站医嘱暂存批次行")
public class DoctorStationAdviceDraftLineRequest {

    /** 行操作：CREATE / UPDATE / DELETE。 */
    @NotBlank(message = "行操作不能为空")
    @Schema(description = "行操作：CREATE / UPDATE / DELETE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lineOp;

    /** 锚定产品定义物理表名，与目录、列表、签发契约一致。 */
    @NotBlank(message = "产品定义表名不能为空")
    @Schema(
        description = "产品定义表名（与 IF-DOR-11 一致）：med_medication_definition / wkf_activity_definition / adm_device_definition",
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
    private SaveMedicationAdviceDraftLinePayload medicationPayload;

    /** 诊疗载荷。 */
    @Schema(description = "诊疗载荷")
    private SaveServiceAdviceDraftLinePayload servicePayload;

    /** 耗材载荷。 */
    @Schema(description = "耗材载荷")
    private SaveDeviceAdviceDraftLinePayload devicePayload;
}
