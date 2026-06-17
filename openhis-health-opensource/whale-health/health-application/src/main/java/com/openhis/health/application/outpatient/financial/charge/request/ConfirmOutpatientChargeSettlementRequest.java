/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.financial.charge.request;

import com.openhis.health.application.outpatient.financial.charge.dto.PaymentDetailSubmitDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门诊正式结算请求（IF-OP-CHG-06，供本地落账与 OP_SETTLE 协同快照回放）。
 */
@Data
@Accessors(chain = true)
public class ConfirmOutpatientChargeSettlementRequest {

    /** 预结算返回的支付草稿主键 {@code fin_payment_reconciliation.id}。 */
    private Long paymentDraftId;

    /** 门诊就诊主键。 */
    private Long encounterId;

    /** 本次结算收费项 id 列表。 */
    private List<Long> chargeItemIds;

    /** 支付渠道明细（现金/个账等）。 */
    private List<PaymentDetailSubmitDto> paymentDetails;

    /** 账单日期，落账时写入支付单。 */
    private LocalDateTime billDate;

    /** 医保结算 id 列表序列化串（平台 2207 成功后回写）。 */
    private String ybSettleIds;

    /** 医保收费批次号（预结算返回，单组兼容）。 */
    private String ybChrgBchno;

    /** 医保收费批次号列表（混合结算多组）。 */
    private List<String> ybChrgBchnos;

    /** 结算结果枚举 code。 */
    private String outcomeEnum;
}
