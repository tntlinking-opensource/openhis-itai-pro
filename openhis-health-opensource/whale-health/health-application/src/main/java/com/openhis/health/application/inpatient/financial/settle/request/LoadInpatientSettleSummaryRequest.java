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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院结算摘要请求（IF-IP-STL-03）。
 */
@Data
@Accessors(chain = true)
/** 住院结算摘要请求。 */
@Schema(description = "住院结算摘要请求", requiredProperties = {"encounterId", "patientId"})
public class LoadInpatientSettleSummaryRequest {

    /** 住院就诊 ID。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 住院就诊 ID（业务字段）。 */
    private Long encounterId;

    /** 患者 ID（一致性校验）。 */
    @NotNull(message = "患者标识不能为空")
    /** 患者 ID。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者 ID（业务字段）。 */
    private Long patientId;

    /** 前端已勾选收费明细；空则按全部待结算计选中合计。 */
    @Schema(description = "已勾选收费明细 ID 列表")
    /** 已勾选收费明细 ID 列表（业务字段）。 */
    private List<Long> selectedChargeItemIds;
}
