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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettlePaymentDetailDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
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
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者 ID。 */
    @NotNull(message = "患者标识不能为空")
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 预结算返回的支付草稿 ID。 */
    @NotNull(message = "支付草稿标识不能为空")
    @Schema(description = "支付草稿 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentDraftId;

    /** 须与草稿绑定的收费明细 ID 列表。 */
    @NotEmpty(message = "收费明细不能为空")
    @Schema(description = "收费明细 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> chargeItemIds;

    /** 渠道实付明细。 */
    @NotEmpty(message = "支付明细不能为空")
    @Schema(description = "支付渠道明细", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<InpatientSettlePaymentDetailDto> paymentDetails;

    /** 结算时间；缺省为服务端当前时刻。 */
    @Schema(description = "结算时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime billDate;
}
