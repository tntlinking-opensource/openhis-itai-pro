/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.math.BigDecimal;

/**
 * 住院预结算请求（IF-IP-STL-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院预结算请求", requiredProperties = {"encounterId", "patientId", "chargeItemIds"})
public class PreSettleInpatientChargesRequest {

    /** 住院就诊 ID。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者 ID。 */
    @NotNull(message = "患者标识不能为空")
    /** 患者 ID。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 所选待结算收费明细 ID 列表；零元出院全结时可为空数组。 */
    @NotNull(message = "收费明细列表不能为 null")
    /** 所选收费明细 ID 列表。 */
    @Schema(description = "所选收费明细 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> chargeItemIds;

    /** 医保合同 ID（登记账户为医保时）。 */
    @Schema(description = "医保合同 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 就诊凭证类型（读卡）。 */
    @Schema(description = "就诊凭证类型")
    private String ybMediumType;

    /** 就诊凭证编号（读卡）。 */
    @Schema(description = "就诊凭证编号")
    private String ybCardInfo;

    /** 人员编号（1101）。 */
    @Schema(description = "人员编号")
    private String psnNo;

    /** 参保地医保区划。 */
    @Schema(description = "参保地医保区划")
    private String insuplcAdmdvs;

    /** 险种类型。 */
    @Schema(description = "险种类型")
    private String insutype;

    /** 个人账户使用标志，默认 1。 */
    @Schema(description = "个人账户使用标志")
    private String acctUsedFlag;

    /** 个人结算方式，默认 01。 */
    @Schema(description = "个人结算方式")
    private String psnSetlway;

    /** 结算模式：DISCHARGE_FULL | MID_SETTLE。 */
    @Schema(description = "结算模式")
    private String settleMode;

    /**
     * 款员所选折扣率 (0,1]；仅能力门控开启时可传（BR-IP-STL-020～023）。
     */
    @Schema(description = "款员所选折扣率")
    private BigDecimal cashierDiscountRate;
}
