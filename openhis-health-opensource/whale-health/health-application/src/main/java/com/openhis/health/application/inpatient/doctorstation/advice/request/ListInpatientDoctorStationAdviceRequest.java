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
 * <p>列表主数据来自 OrderInfo 读模型；{@link #therapyTypeCode} 可选过滤长/临/出院带药
 * （LONG_TERM / TEMPORARY / DISCHARGE_MEDICATION）。
 * {@link #includeTcmMedication} 为 true 时纳入中药饮片（病案首页医嘱侧栏）；默认排除以保持开立 Tab 分域。</p>
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

    /** 治疗类型筛选：LONG_TERM / TEMPORARY / DISCHARGE_MEDICATION；空表示全部。 */
    @Schema(description = "治疗类型筛选：LONG_TERM / TEMPORARY / DISCHARGE_MEDICATION；空表示全部")
    private String therapyTypeCode;

    /**
     * 是否纳入中药饮片行（按方在前端合并展示）。
     * <p>默认 {@code false}：开立医嘱 Tab 与中医 Tab 分域，排除 {@code tcmFlag=true}。
     * 病案首页医嘱侧栏传 {@code true}，与西药/诊疗同一列表回显。</p>
     */
    @Schema(description = "是否纳入中药饮片（病案首页医嘱列表为 true；开立 Tab 默认 false）")
    private Boolean includeTcmMedication;
}
