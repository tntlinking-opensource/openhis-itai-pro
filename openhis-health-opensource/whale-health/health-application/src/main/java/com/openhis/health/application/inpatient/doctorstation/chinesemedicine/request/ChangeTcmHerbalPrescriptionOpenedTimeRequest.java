/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IF-IP-TCM-42：按方改中药开立时间请求。
 *
 * <p>{@code prescriptionNos} 与 {@code prescriptionGroupKeys} 至少提供其一；
 * 已签发方优先用处方号，草稿方用前端方标识。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "按方改中药开立时间请求")
public class ChangeTcmHerbalPrescriptionOpenedTimeRequest {

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

    /**
     * 已签发处方号列表（按方扩行）；可与 {@link #prescriptionGroupKeys} 同时传入。
     */
    @Schema(description = "已签发处方号列表")
    private List<String> prescriptionNos;

    /**
     * 草稿方前端标识列表（按方扩行）；无处方号时使用。
     */
    @Schema(description = "草稿方 prescriptionGroupKey 列表")
    private List<String> prescriptionGroupKeys;

    /** 新开立时间（整方各味统一写入 {@code req_opened_time}）。 */
    @NotNull(message = "新开立时间不能为空")
    @Schema(description = "新开立时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime newOpenedTime;
}
