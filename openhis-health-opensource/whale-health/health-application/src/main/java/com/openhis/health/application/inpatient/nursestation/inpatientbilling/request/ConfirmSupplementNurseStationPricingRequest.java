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
 * IF-IP-PRC-04：医嘱补费确认请求（BR-IP-PRC-005）。
 *
 * <p>每行须带 {@link com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingParentRequestAnchorDto}；
 * 父医嘱须医生开立且状态 VERIFIED/STOPPED。</p>
 */
@Data
@Accessors(chain = true)
/** 医嘱补费确认请求。 */
@Schema(description = "医嘱补费确认请求")
public class ConfirmSupplementNurseStationPricingRequest {

    /** 补费写明细；每行 parentRequestAnchor 非空。 */
    @NotEmpty(message = "补费行不能为空")
    @Schema(description = "补费行列表（每行须 parentRequestAnchor）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<NurseStationPricingWriteItemDto> items;
}
