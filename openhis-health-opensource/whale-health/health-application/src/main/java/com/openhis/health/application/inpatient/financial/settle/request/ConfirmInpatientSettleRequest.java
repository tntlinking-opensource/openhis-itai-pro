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

package com.openhis.health.application.inpatient.financial.settle.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettlePaymentDetailDto;
import com.openhis.health.integration.payment.dto.ClientScanResultDto;
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
/** 确认住院结算请求。 */
@Schema(description = "确认住院结算请求", requiredProperties = {
    "encounterId", "patientId", "paymentDraftId", "chargeItemIds", "paymentDetails"
})
public class ConfirmInpatientSettleRequest {

    /** 住院就诊 ID。 */
    @NotNull(message = "就诊标识不能为空")
    /** 住院就诊 ID。 */
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 住院就诊 ID（业务字段）。 */
    private Long encounterId;

    /** 患者 ID。 */
    @NotNull(message = "患者标识不能为空")
    /** 患者 ID。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者 ID（业务字段）。 */
    private Long patientId;

    /** 预结算返回的支付草稿 ID。 */
    @NotNull(message = "支付草稿标识不能为空")
    /** 支付草稿 ID。 */
    @Schema(description = "支付草稿 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 支付草稿 ID（业务字段）。 */
    private Long paymentDraftId;

    /** 须与草稿绑定的收费明细 ID 列表；零元出院全结时可为空数组。 */
    @NotNull(message = "收费明细列表不能为 null")
    /** 收费明细 ID 列表。 */
    @Schema(description = "收费明细 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 收费明细 ID 列表（业务字段）。 */
    private List<Long> chargeItemIds;

    /** 渠道实付明细；预交金充足无需渠道收款时可为空数组。 */
    @NotNull(message = "支付明细不能为 null")
    /** 支付渠道明细。 */
    @Schema(description = "支付渠道明细", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 支付渠道明细（业务字段）。 */
    private List<InpatientSettlePaymentDetailDto> paymentDetails;

    /** 结算时间；缺省为服务端当前时刻。 */
    @Schema(description = "结算时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 结算时间（业务字段）。 */
    private LocalDateTime billDate;

    /** 医保预结算快照 ID；缺省时按 paymentDraftId 查 ACTIVE。 */
    @Schema(description = "医保预结算快照 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 医保预结算快照 ID（业务字段）。 */
    private Long ybPreSettleId;

    /** 银联被扫 Win 回传（少补扫码收款时必填）。 */
    @Schema(description = "银联被扫 Win 回传")
    private ClientScanResultDto clientScanResult;
}
