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

package com.openhis.health.application.inpatient.nursestation.dispensesummary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 汇总单头（IF-IP-SUM-02/03）。
 */
@Data
@Accessors(chain = true)
/** 汇总单头。 */
@Schema(description = "汇总单头")
public class NurseStationDispenseSummarySlipHeaderDto {

    @Schema(description = "汇总单号")
    private String busNo;

    @Schema(description = "申请时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime applyTime;

    /** 发药时间（药房核发后）。 */
    @Schema(description = "发药时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dispenseTime;

    /** 发放药房主键。 */
    @Schema(description = "发放药房主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long purposeLocationId;

    /** 发放药房名称。 */
    @Schema(description = "发放药房名称")
    private String purposeLocationName;

    /** 申请人主键。 */
    @Schema(description = "申请人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicantId;

    /** 申请人姓名。 */
    @Schema(description = "申请人姓名")
    private String applicantName;

    /** 领药人主键（药房核发后写入 SupplyDelivery）。 */
    @Schema(description = "领药人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long receiverId;

    /** 领药人姓名。 */
    @Schema(description = "领药人姓名")
    private String receiverName;

    /** 汇总单审批态 code（SupplyRequest.status_enum）。 */
    @Schema(description = "审批态 code")
    private String supplyStatusEnum;

    /** 汇总单审批态展示。 */
    @Schema(description = "审批态展示")
    private String supplyStatusEnumText;

    /** 发放态 code（药房核发后 SupplyDelivery.status_enum）。 */
    @Schema(description = "发放态 code")
    private String deliveryStatusEnum;

    /** 发放态展示。 */
    @Schema(description = "发放态展示")
    private String deliveryStatusEnumText;
}
