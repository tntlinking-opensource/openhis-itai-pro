/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院费用明细单批上传请求（2301，供进度条分批）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院费用明细单批上传请求", requiredProperties = {"encounterId", "patientId", "chargeItemIds"})
public class UploadInpatientFeeDetailsBatchRequest {

    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @NotNull(message = "患者标识不能为空")
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    @NotNull(message = "收费明细列表不能为 null")
    @Schema(description = "所选收费明细 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> chargeItemIds;

    @Schema(description = "医保合同 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    @Schema(description = "就诊凭证类型")
    private String ybMediumType;

    @Schema(description = "就诊凭证编号")
    private String ybCardInfo;

    @Schema(description = "人员编号")
    private String psnNo;

    @Schema(description = "参保地医保区划")
    private String insuplcAdmdvs;

    @Schema(description = "险种类型")
    private String insutype;

    @Schema(description = "个人账户使用标志")
    private String acctUsedFlag;

    @Schema(description = "个人结算方式")
    private String psnSetlway;

    @Schema(description = "结算模式：DISCHARGE_FULL | MID_SETTLE")
    private String settleMode;

    /** 上批返回的平台就诊 ID；首批可空。 */
    @Schema(description = "上批返回的平台就诊 ID")
    private String mdtrtId;

    /**
     * 医护站完整预结算无卡试算。为 true 时允许人员编号为空，由医保上下文从入院资料回填。
     */
    @Schema(description = "医护站无卡试算：不读卡")
    private Boolean cardlessTrial;
}
