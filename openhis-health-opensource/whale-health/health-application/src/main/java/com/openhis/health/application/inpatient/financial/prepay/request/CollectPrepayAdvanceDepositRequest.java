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

package com.openhis.health.application.inpatient.financial.prepay.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.financial.prepay.dto.PrepayPaymentDetailDto;
import com.openhis.health.integration.payment.dto.ClientScanResultDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收取预交金（续收）请求（IF-IP-PREP-03）。
 */
@Data
@Accessors(chain = true)
/** 收取预交金请求。 */
@Schema(description = "收取预交金请求")
public class CollectPrepayAdvanceDepositRequest {

    @NotNull(message = "住院就诊 ID 不能为空")
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @NotNull(message = "患者 ID 不能为空")
    /** 患者 ID。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    @NotNull(message = "收取金额不能为空")
    /** 收取金额。 */
    @Schema(description = "收取金额，须大于 0", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    @NotNull(message = "支付明细不能为空")
    /** 支付明细。 */
    @Schema(description = "支付明细，至少一条", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<PrepayPaymentDetailDto> paymentDetails;

    /** 扫码支付码。 */
    @Schema(description = "扫码支付码（三方分支）")
    private String paymentCode;

    /** 操作员 ID；缺省取当前登录用户。 */
    @Schema(description = "操作员 ID；缺省取当前登录用户")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 银联被扫 Win 回传（扫码收预交金时必填）。 */
    @Schema(description = "银联被扫 Win 回传")
    private ClientScanResultDto clientScanResult;

    /** 预下单草稿 ID（{@link #preparePrepayAdvanceDeposit} 返回；扫码续收时传入）。 */
    @Schema(description = "预交金支付草稿 ID（扫码预下单后传入）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentDraftId;
}
