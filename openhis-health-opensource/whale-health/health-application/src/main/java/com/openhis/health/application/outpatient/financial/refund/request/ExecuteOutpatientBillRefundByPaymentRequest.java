/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.refund.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.financial.refund.dto.RefundPaymentDetailSubmitDto;
import com.openhis.health.infrastructure.integration.payment.dto.ClientRefundResultDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 按原支付主单执行门诊账单退费请求（IF-REF-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按原支付主单执行门诊账单退费请求")
public class ExecuteOutpatientBillRefundByPaymentRequest {

    /** 就诊主键。 */
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 原支付主单主键。 */
    @NotNull(message = "originalPaymentId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 原支付主单主键。 */
    @Schema(description = "原支付主单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long originalPaymentId;

    /** 本笔拟退收费明细主键集合。 */
    @NotEmpty(message = "refundingChargeItemIds 不能为空")
    /** 拟退收费明细主键集合。 */
    @Schema(description = "拟退收费明细主键集合", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> refundingChargeItemIds;

    /** 半退保留再生收费明细主键集合；全退可空。 */
    @Schema(description = "半退保留再生收费明细主键集合")
    private List<Long> preservedNewChargeItemIds;

    /** 渠道实退拆分明细。 */
    @NotEmpty(message = "refundPaymentDetails 不能为空")
    /** 渠道实退拆分明细。 */
    @Schema(description = "渠道实退拆分明细", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<RefundPaymentDetailSubmitDto> refundPaymentDetails;

    /**
     * 账单日文本；空则取当前时刻。
     *
     * <p>格式与 {@link com.openhis.health.domain.share.constants.GeneralSharedConstants#DATE_TIME_PATTERN} 一致。</p>
     */
    /** 账单日，格式 yyyy-MM-dd HH:mm:ss（业务字段）。 */
    @Schema(description = "账单日，格式 yyyy-MM-dd HH:mm:ss")
    private String billDate;

    /** 银联退货 Win 回传（原扫码段原路退时必填）。 */
    @Schema(description = "银联退货 Win 回传")
    private ClientRefundResultDto clientRefundResult;
}
