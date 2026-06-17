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

package com.openhis.health.application.inpatient.financial.settle.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 确认住院结算响应（IF-IP-STL-06）。
 */
@Data
@Accessors(chain = true)
/** 确认住院结算响应。 */
@Schema(description = "确认住院结算响应")
public class ConfirmInpatientSettleResponse {

    @Schema(description = "成功支付主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 成功支付主键（业务字段）。 */
    private Long paymentId;

    /** 发票草稿 ID。 */
    @Schema(description = "发票草稿 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 发票草稿 ID（业务字段）。 */
    private Long invoiceDraftId;

    /** 是否本次为出院全结。 */
    @Schema(description = "是否本次为出院全结")
    /** 是否本次为出院全结（业务字段）。 */
    private Boolean dischargeFullSettle;

    /** 出院多退预交金支付单 ID；无多退时为 null。 */
    @Schema(description = "出院多退预交金支付单 ID；无多退时为 null")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 出院多退预交金支付单 ID；无多退时为 null（业务字段）。 */
    private Long advanceRefundPaymentId;

    /** 成功语义码。 */
    @Schema(description = "成功语义码")
    /** 成功语义码（业务字段）。 */
    private String successMessageCode;

    /** 医保结算 ID。 */
    @Schema(description = "医保结算 ID（2304 setl_id）")
    /** 医保结算 ID（2304 setl_id）（业务字段）。 */
    private String ybSetlId;

    /** 是否已完成医保正式结算。 */
    @Schema(description = "是否已完成医保正式结算")
    /** 是否已完成医保正式结算（业务字段）。 */
    private Boolean insuranceSettled;

    /** 结算模式：SYNC | INSURANCE_ASYNC。 */
    @Schema(description = "结算模式：SYNC | INSURANCE_ASYNC")
    /** 结算模式：SYNC | INSURANCE_ASYNC（业务字段）。 */
    private String settlementMode;

    /** 支付状态：SETTLING | SUCCESS。 */
    @Schema(description = "支付状态：SETTLING | SUCCESS")
    /** 支付状态：SETTLING | SUCCESS（业务字段）。 */
    private String paymentStatus;

    /** 医保协同单 ID 列表。 */
    @Schema(description = "医保协同单 ID 列表（异步模式）")
    /** 医保协同单 ID 列表（异步模式）（业务字段）。 */
    private java.util.List<Long> coordinationIds;
}
