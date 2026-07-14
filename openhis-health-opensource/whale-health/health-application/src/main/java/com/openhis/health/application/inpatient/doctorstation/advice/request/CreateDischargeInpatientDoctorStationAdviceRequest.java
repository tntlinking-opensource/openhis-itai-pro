/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * IF-IP-DOR-21：开立出院医嘱请求（创建出院 {@code ServiceRequest} 并记录离院里程碑）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-开立出院医嘱请求")
public class CreateDischargeInpatientDoctorStationAdviceRequest {

    /** 就诊标识。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_ENCOUNTER_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 与住院就诊绑定的患者主键，由 AppService 与读模型二次校验一致。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_PATIENT_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 计划出院时间；为空时领域层按当前时刻落库。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 计划出院时间；空则当前时间。 */
    @Schema(description = "计划出院时间；空则当前时间")
    private LocalDateTime plannedDischargeTime;

    /** 出院方式字典 code（如医嘱离院、转院等），写入服务申请类目相关字段。 */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_DISCHARGE_MODE_REQUIRED)
    /** 出院方式字典 code。 */
    @Schema(description = "出院方式字典 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String outWayCode;

    /** 出院原因。 */
    @Schema(description = "出院原因")
    private String reasonText;

    /** 转出前所属科室组织主键（原科室）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 原科室。 */
    @Schema(description = "原科室")
    private Long sourceOrgId;

    /** 转出前病区或床位位置主键（原病区/床位）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 原病区/床位。 */
    @Schema(description = "原病区/床位")
    private Long sourceLocationId;

    /** 离院后目标科室；纯离院场景可为空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 目标科室。 */
    @Schema(description = "目标科室")
    private Long targetOrgId;

    /** 离院后目标位置（如转科床位）；纯离院场景可为空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 目标位置。 */
    @Schema(description = "目标位置")
    private Long targetLocationId;
}
