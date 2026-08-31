/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.infrastructure.integration.payment.dto.ClientRefundResultDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 体检收银员执行退费请求（IF-PE-CHG-09）。
 *
 * <p>收银员直接发起，无需医生站申请；原支付须整单冲退。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检收银员执行退费请求")
public class RefundPhysicalExamChargesRequest {

    /** 体检就诊主键。 */
    @Schema(description = "体检就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 原支付主单主键。 */
    @Schema(description = "原支付主单主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long originalPaymentId;

    /** 拟退收费明细主键（须覆盖原支付快照整单）。 */
    @Schema(description = "拟退收费明细主键")
    private List<Long> refundingChargeItemIds;

    /**
     * 账单日文本；空则取当前时刻。
     *
     * <p>格式与 {@link com.openhis.health.domain.share.constants.GeneralSharedConstants#DATE_TIME_PATTERN} 一致。</p>
     */
    @Schema(description = "账单日，格式 yyyy-MM-dd HH:mm:ss")
    private String billDate;

    /** 银联退货 Win 回传（原扫码段原路退时必填）。 */
    @Schema(description = "银联退货 Win 回传")
    private ClientRefundResultDto clientRefundResult;
}
