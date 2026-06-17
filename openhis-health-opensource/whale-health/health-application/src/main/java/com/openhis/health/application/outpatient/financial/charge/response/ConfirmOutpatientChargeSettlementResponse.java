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

package com.openhis.health.application.outpatient.financial.charge.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊正式结算响应。
 */
@Data
@Accessors(chain = true)
/** 门诊正式结算响应。 */
@Schema(description = "门诊正式结算响应")
public class ConfirmOutpatientChargeSettlementResponse {
    @Schema(description = "正式支付主键")
    /** 正式支付主键（业务字段）。 */
    private Long paymentId;
    @Schema(description = "票据草稿主键")
    /** 票据草稿主键（业务字段）。 */
    private Long invoiceId;
    /** 成功语义码。 */
    @Schema(description = "成功语义码")
    /** 成功语义码（业务字段）。 */
    private String successMessageCode;
    /** 结算模式：SYNC | INSURANCE_ASYNC */
    @Schema(description = "结算模式：SYNC | INSURANCE_ASYNC")
    /** 结算模式：SYNC | INSURANCE_ASYNC（业务字段）。 */
    private String settlementMode;
    /** 支付状态：SETTLING | SUCCESS */
    @Schema(description = "支付状态：SETTLING | SUCCESS")
    /** 支付状态：SETTLING | SUCCESS（业务字段）。 */
    private String paymentStatus;
    /** 医保协同单 ID 列表（异步模式） */
    @Schema(description = "医保协同单 ID 列表（异步模式）")
    /** 医保协同单 ID 列表（异步模式）（业务字段）。 */
    private java.util.List<Long> coordinationIds;
    /** 医保结算 ID（2207 setl_id，多组逗号分隔） */
    @Schema(description = "医保结算 ID")
    /** 医保结算 ID（业务字段）。 */
    private String ybSetlId;
}
