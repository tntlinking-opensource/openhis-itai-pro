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

package com.openhis.health.application.inpatient.pharmacy.dispense.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院作废发放响应（IF-IPP-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院作废发放响应")
public class CancelInpatientMedicationDispenseResponse {

    /** 作废领域命令是否全部成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 面向用户的提示或错误摘要。 */
    @Schema(description = "提示文案")
    private String message;

    /** 已作废为 DECLINED 的发放单主键列表。 */
    @Schema(description = "已处理发放单主键集合")
    private List<Long> processedDispenseIds;
}
