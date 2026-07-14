/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-10：加载当前就诊医嘱统一列表请求。
 *
 * <p>列表主数据来自 OrderInfo 读模型；{@link #therapyTypeCode} 可选过滤长临（LONG_TERM / TEMPORARY）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-加载当前就诊医嘱统一列表请求")
public class ListInpatientDoctorStationAdviceRequest {

    /** 就诊标识。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_ENCOUNTER_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者标识。 */
    @NotNull(message = DoctorStationAdviceConstants.MSG_PATIENT_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 长临医嘱筛选：LONG_TERM / TEMPORARY；空表示全部。 */
    @Schema(description = "长临医嘱筛选：LONG_TERM / TEMPORARY；空表示全部")
    private String therapyTypeCode;
}
