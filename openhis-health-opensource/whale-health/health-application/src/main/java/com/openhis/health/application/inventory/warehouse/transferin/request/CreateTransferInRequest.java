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

package com.openhis.health.application.inventory.warehouse.transferin.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inventory.warehouse.transferin.dto.TransferInLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 新建药房入库单请求（IF-PHA-IN-04）。
 *
 * {@code practitionerId} 为经手人医务人员主键，须显式传入；{@code applicant_id} 由服务端绑定当前登录医务人员。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(
    description = "新建药房入库单 IF-PHA-IN-04",
    requiredProperties = {"purposeLocationId", "sourceLocationId", "categoryEnum", "practitionerId", "lines"}
)
public class CreateTransferInRequest {

    @NotNull(message = "purposeLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 目的药房主键。 */
    @Schema(description = "目的药房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long purposeLocationId;

    @NotNull(message = "sourceLocationId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 来源药库主键。 */
    @Schema(description = "来源药库主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceLocationId;

    /** 源位置类型 SupplyRequestType#getCode。 */
    @Schema(description = "源位置类型 SupplyRequestType#getCode，可空由后端按库位推导")
    private String sourceTypeEnum;

    /** 目的位置类型 SupplyRequestType#getCode。 */
    @Schema(description = "目的位置类型 SupplyRequestType#getCode，可空由后端按库位推导")
    private String purposeTypeEnum;

    @NotNull(message = "categoryEnum 不能为空")
    /** 供应类别 SupplyCategory#getCode。 */
    @Schema(description = "供应类别 SupplyCategory#getCode", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryEnum;

    /** 关联上游业务单号。 */
    @Schema(description = "关联上游业务单号，可空（BR-PHA-IN-003）")
    private String originalBusNo;

    /** 显式业务单号。 */
    @Schema(description = "显式业务单号，可空由领域按日生成")
    private String busNo;

    /** 经手人（医务人员主键），与出库单 {@code practitionerId} 语义一致。 */
    @NotNull(message = "经手人不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 经手人 practitionerId。 */
    @Schema(description = "经手人 practitionerId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long practitionerId;

    @NotEmpty(message = "明细不能为空")
    /** 批号级明细。 */
    @Schema(description = "批号级明细", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<TransferInLineDto> lines;
}
