/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.prepay.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inpatient.financial.prepay.dto.PrepayPaymentDetailDto;
import com.zxplat.domain.infrastructure.integration.payment.dto.ClientRefundResultDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退预交金请求（IF-IP-PREP-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "退预交金请求")
public class RefundPrepayAdvanceDepositRequest {

    @NotNull(message = "住院就诊 ID 不能为空")
    /** 住院就诊 ID。 */
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @NotNull(message = "患者 ID 不能为空")
    /** 患者 ID。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    @NotNull(message = "退费金额不能为空")
    /** 退费金额。 */
    @Schema(description = "退费金额，须大于 0 且不超过账户余额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal refundAmount;

    @NotNull(message = "退现明细不能为空")
    /** 退现渠道明细。 */
    @Schema(description = "退现渠道明细，至少一条", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<PrepayPaymentDetailDto> paymentDetails;

    /** 操作员 ID；缺省取当前登录用户。 */
    @Schema(description = "操作员 ID；缺省取当前登录用户")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 原路退对应的收取支付单 ID（扫码原路退时必填）。 */
    @Schema(description = "原收取支付单 ID（扫码原路退）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long originalPaymentId;

    /** 银联退货 Win 回传（扫码原路退时必填）。 */
    @Schema(description = "银联退货 Win 回传")
    private ClientRefundResultDto clientRefundResult;
}
