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

package com.openhis.health.application.inpatient.nursestation.orderaudit.request;

import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditWriteItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量校对通过请求（IF-IP-AUD-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量校对通过请求", requiredProperties = {"items"})
public class VerifyNurseStationOrdersRequest {

    /** 待校对医嘱行（1～50 条，BR-IP-AUD-010）。 */
    @NotEmpty(message = "校对项不能为空")
    @Valid
    @Schema(description = "待校对医嘱行（1～50 条）", required = true)
    private List<NurseStationOrderAuditWriteItemDto> items;
}
