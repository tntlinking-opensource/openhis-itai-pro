/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftLineRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-23：暂存前绑定解析请求（与 IF-DOR-14 暂存变更行同构，纯读）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-暂存前绑定解析请求")
public class ResolveDoctorStationAdviceDraftBindsRequest {

    /** 就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者标识。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 暂存变更行。 */
    @NotEmpty(message = "暂存变更行不能为空")
    @Valid
    @Schema(description = "暂存变更行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DoctorStationAdviceDraftLineRequest> lines;

    /**
     * 为 true 时跳过用法/诊疗/合管自动带出，直接返回空 bindItems。
     * <p>组套（通用 / 申请单）套用产物签发时使用（**BR-ORD-038** / **BR-OTL-APY-005**）；
     * 缺省或 false 保持既有解析。</p>
     */
    @Schema(description = "true=跳过自动绑定带出（组套套用签发）")
    private Boolean skipAutoBind;
}
