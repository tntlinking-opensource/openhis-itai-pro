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

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingCancelItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-PRC-06：取消划价请求（BR-IP-PRC-021～028）。
 *
 * <p>来自已划价列表多选；1～50 行同事务整行还原，非部分数量冲销。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消划价请求")
public class CancelNurseStationPricingRequest {

    /** 待取消的已划价行；禁止重复 (orderType, requestId)。 */
    @NotEmpty(message = "取消划价行不能为空")
    @Schema(description = "取消划价行列表（1～50）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<NurseStationPricingCancelItemDto> items;
}
