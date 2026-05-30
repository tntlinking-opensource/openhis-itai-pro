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

package com.openhis.health.application.inventory.pharmacy.stocktake.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.pharmacy.stocktake.dto.PharmacyStocktakeDetailLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘点单详情出参（IF-PHA-ST-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "盘点单详情 IF-PHA-ST-03")
public class GetPharmacyStocktakeDetailResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;

    /** 单据状态。 */
    @Schema(description = "状态 statusEnum")
    private String statusEnum;

    /** 盘点药房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "盘点药房 purposeLocationId")
    private Long purposeLocationId;

    /** 盘点药房名称。 */
    @Schema(description = "盘点药房名称 purposeLocationName")
    private String purposeLocationName;

    /** 经手人。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经手人 practitionerId")
    private Long practitionerId;

    /** 申请人。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "申请人 applicantId")
    private Long applicantId;

    /** 申请时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "申请时间 applyTime")
    private LocalDateTime applyTime;

    /** 审批人。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人 approverId")
    private Long approverId;

    /** 审批时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;

    /** 明细行。 */
    @Schema(description = "明细行 lines")
    private List<PharmacyStocktakeDetailLineDto> lines;
}
