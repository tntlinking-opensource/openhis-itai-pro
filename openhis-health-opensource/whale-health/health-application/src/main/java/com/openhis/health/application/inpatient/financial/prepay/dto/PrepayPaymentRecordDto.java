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

package com.openhis.health.application.inpatient.financial.prepay.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预交金流水行 DTO（IF-IP-PREP-02）。
 */
@Data
@Accessors(chain = true)
/** 预交金支付流水。 */
@Schema(description = "预交金支付流水")
public class PrepayPaymentRecordDto {

    @Schema(description = "支付流水 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 收费/退费时间。 */
    @Schema(description = "收费/退费时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime tradeTime;

    /** 业务类型展示：收预交金/退预交金。 */
    @Schema(description = "业务类型展示：收预交金/退预交金", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bizTypeText;

    /** 收付方向 PAY/UN_PAY。 */
    @Schema(description = "收付方向 PAY/UN_PAY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String paymentEnum;

    /** 金额。 */
    @Schema(description = "金额（正数）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    /** 支付方式展示。 */
    @Schema(description = "支付方式展示")
    private String payMethodText;

    /** 操作员展示名。 */
    @Schema(description = "操作员展示名")
    private String operatorName;

    /** 票据号/支付单号。 */
    @Schema(description = "票据号/支付单号")
    private String ticketNo;

    /** 支付状态 code。 */
    @Schema(description = "支付状态 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnum;

    /** 支付状态展示。 */
    @Schema(description = "支付状态展示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnumText;
}
