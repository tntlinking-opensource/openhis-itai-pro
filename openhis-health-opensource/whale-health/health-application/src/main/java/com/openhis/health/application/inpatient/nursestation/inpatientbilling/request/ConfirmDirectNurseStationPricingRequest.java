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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.request;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-PRC-02：直接划价确认请求（BR-IP-PRC-004）。
 *
 * <p>每行 {@link NurseStationPricingWriteItemDto#getParentRequestAnchor()} 须为空；整批 1～50 行同事务落库。</p>
 */
@Data
@Accessors(chain = true)
/** 直接划价确认请求。 */
@Schema(description = "直接划价确认请求")
public class ConfirmDirectNurseStationPricingRequest {

    /** 划价写明细；禁止携带父锚点。 */
    @NotEmpty(message = "划价行不能为空")
    @Schema(description = "划价行列表（1～50，禁止 parentRequestAnchor）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<NurseStationPricingWriteItemDto> items;
}
