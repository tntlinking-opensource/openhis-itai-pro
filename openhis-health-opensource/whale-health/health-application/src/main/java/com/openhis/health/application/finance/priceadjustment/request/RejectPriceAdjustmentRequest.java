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

package com.openhis.health.application.finance.priceadjustment.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 审批驳回请求（IF-PRC-14）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "审批驳回请求", requiredProperties = {"busNo", "approveComment"})
public class RejectPriceAdjustmentRequest {

    /** 调价单号。 */
    @NotBlank(message = "调价单号不能为空")
    @Schema(description = "调价单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 驳回原因（必填）。 */
    @NotBlank(message = "驳回原因不能为空")
    @Schema(description = "驳回原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private String approveComment;
}
