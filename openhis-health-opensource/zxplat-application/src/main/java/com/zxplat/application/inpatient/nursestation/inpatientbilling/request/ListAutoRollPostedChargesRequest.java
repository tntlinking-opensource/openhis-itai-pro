/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.request;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * IF-IP-ARG-09：自动计费已滚费用记录列表请求。
 *
 * <p>{@code ruleId}（床位配置）与 {@code serviceRequestId}（护理医嘱）二选一；
 * {@code dateFrom}/{@code dateTo} 可选，空表示不限自然日。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "自动计费已滚费用记录列表请求")
public class ListAutoRollPostedChargesRequest {

    /** 住院就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "住院就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /**
     * 自动计费配置（cus_charge_item_auto_gen）主键。
     *
     * <p>与 {@link #serviceRequestId} 二选一：床位配置展开传本字段；护理级别展开传护理医嘱主键。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "自动计费配置主键（与 serviceRequestId 二选一）")
    private Long ruleId;

    /**
     * 护理级别医嘱主键。
     *
     * <p>与 {@link #ruleId} 二选一：护理只读行展开时传入。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "护理级别医嘱主键（与 ruleId 二选一）")
    private Long serviceRequestId;

    /** 业务日起（含）；空表示不限起。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "业务日起（含）")
    private LocalDate dateFrom;

    /** 业务日止（含）；空表示不限止。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "业务日止（含）")
    private LocalDate dateTo;
}
