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

package com.openhis.health.application.inpatient.nursestation.dispensesummary.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 撤销汇总领药单请求（IF-IP-SUM-08）。
 *
 * <p>仅允许 {@link com.openhis.health.domain.share.enums.SupplyStatus#PENDING_APPROVAL} 待发送汇总单。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
/** 撤销汇总领药单 IF-IP-SUM-08。 */
@Schema(description = "撤销汇总领药单 IF-IP-SUM-08", requiredProperties = {"summaryNos"})
public class CancelMedicationSummaryRequest {

    /** 待撤销汇总单号（1～50）。 */
    @NotEmpty(message = "请至少选择一张汇总单")
    @Schema(description = "汇总单号列表", required = true)
    private List<String> summaryNos;
}
