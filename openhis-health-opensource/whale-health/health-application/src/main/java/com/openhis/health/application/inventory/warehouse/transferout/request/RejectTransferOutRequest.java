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

package com.openhis.health.application.inventory.warehouse.transferout.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 *
 * <p>原因码与展示文案的合并与落库规则见 Mapper 装配注释。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class RejectTransferOutRequest {

    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    @Schema(description = "驳回原因码")
    private String changeReasonCode;

    /** 驳回原因人读说明，可与码组合写入变更说明。 */
    @Schema(description = "驳回原因展示")
    private String changeReason;
}
