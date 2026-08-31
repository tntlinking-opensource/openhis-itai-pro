/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleClientRefundItemDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettlePaymentDetailDto;
import com.openhis.health.infrastructure.integration.payment.dto.ClientScanResultDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 确认住院结算请求（IF-IP-STL-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "确认住院结算请求", requiredProperties = {
    "encounterId", "patientId", "paymentDraftId", "chargeItemIds", "paymentDetails"
})
public class ConfirmInpatientSettleRequest {

    /** 住院就诊 ID。 */
    @NotNull(message = "就诊标识不能为空")
    /** 住院就诊 ID。 */
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者 ID。 */
    @NotNull(message = "患者标识不能为空")
    /** 患者 ID。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 预结算返回的支付草稿 ID。 */
    @NotNull(message = "支付草稿标识不能为空")
    /** 支付草稿 ID。 */
    @Schema(description = "支付草稿 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentDraftId;

    /** 须与草稿绑定的收费明细 ID 列表；零元出院全结时可为空数组。 */
    @NotNull(message = "收费明细列表不能为 null")
    /** 收费明细 ID 列表。 */
    @Schema(description = "收费明细 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> chargeItemIds;

    /** 渠道实付明细；预交金充足无需渠道收款时可为空数组。 */
    @NotNull(message = "支付明细不能为 null")
    /** 支付渠道明细。 */
    @Schema(description = "支付渠道明细", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<InpatientSettlePaymentDetailDto> paymentDetails;

    /** 结算时间；缺省为服务端当前时刻。 */
    @Schema(description = "结算时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime billDate;

    /** 医保预结算快照 ID；缺省时按 paymentDraftId 查 ACTIVE。 */
    @Schema(description = "医保预结算快照 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ybPreSettleId;

    /** 银联被扫 Win 回传（渠道现付扫码收款时必填）。 */
    @Schema(description = "银联被扫 Win 回传")
    private ClientScanResultDto clientScanResult;

    /**
     * 医保出院原路退预交金：按原收款单挂接的 Win 退货回传列表。
     */
    @Schema(description = "医保出院预交金原路退 Win 回传列表")
    private List<InpatientSettleClientRefundItemDto> clientRefundResults;
}
