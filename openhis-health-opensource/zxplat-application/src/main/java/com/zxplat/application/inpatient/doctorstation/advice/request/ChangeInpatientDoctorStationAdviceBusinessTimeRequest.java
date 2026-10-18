/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.DoctorStationAdviceConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * IF-IP-DOR-26：改住院医嘱开立时间或停嘱时间（禁止改签发时间）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-改医嘱业务时间请求")
public class ChangeInpatientDoctorStationAdviceBusinessTimeRequest {

    /** 就诊标识。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_ENCOUNTER_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 与就诊绑定的患者主键。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_PATIENT_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 药/诊请求主键。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_CHANGE_BUSINESS_TIME_REQUEST_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药/诊请求主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long requestId;

    /** 产品定义表名，区分药/诊。 */
    @NotBlank(message = DoctorStationAdviceConstants.MSG_CHANGE_BUSINESS_TIME_PRODUCT_TABLE_REQUIRED)
    @Schema(description = "产品定义表名（药/诊）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;

    /**
     * 时间种类：{@code OPENED} 改开立时间；{@code STOP} 改停嘱时间
     * （{@link com.zxplat.domain.share.enums.RequestBusinessTimeKind}）。
     */
    @NotBlank(message = DoctorStationAdviceConstants.MSG_CHANGE_BUSINESS_TIME_KIND_REQUIRED)
    @Schema(description = "时间种类 OPENED/STOP", requiredMode = Schema.RequiredMode.REQUIRED)
    private String timeKind;

    /** 新业务时间。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_CHANGE_BUSINESS_TIME_NEW_TIME_REQUIRED)
    @Schema(description = "新业务时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime newTime;
}
