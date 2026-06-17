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

package com.openhis.health.application.inpatient.pharmacy.summarydispense.response;

import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummaryDispenseItemDto;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipHeaderDto;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 查询汇总单详情响应（IF-IPS-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 查询汇总单详情响应。 */
@Schema(description = "查询汇总单详情响应")
public class GetMedicationSummaryDispenseDetailResponse {

    @Schema(description = "汇总单号")
    private String summaryNo;

    /** 单头。 */
    @Schema(description = "单头")
    private InpatientPharmacySummarySlipHeaderDto header;

    /** 合并 Supply 行（无批号）。 */
    @Schema(description = "合并明细行")
    private List<InpatientPharmacySummarySlipLineDto> mergedLines;

    /** 底层发放行（含批号/扣库字段）。 */
    @Schema(description = "发放行明细")
    private List<InpatientPharmacySummaryDispenseItemDto> dispenseItems;
}
