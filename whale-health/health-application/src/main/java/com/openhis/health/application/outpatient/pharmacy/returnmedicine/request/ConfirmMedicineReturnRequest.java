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

package com.openhis.health.application.outpatient.pharmacy.returnmedicine.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 确认或拒绝退药请求（IF-PHA-02-04）。
 */
@Data
@Schema(description = "确认或拒绝退药请求")
public class ConfirmMedicineReturnRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 退药项列表。 */
    @Schema(description = "退药项列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ReturnMedicineItemRequest> items;

    /** 操作类型（CONFIRM / DECLINE）。 */
    @Schema(description = "操作类型（CONFIRM / DECLINE）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String confirmType;

    /** 退药/拒绝原因；拒绝时必填。 */
    @Schema(description = "退药/拒绝原因；拒绝时必填")
    private String reason;
}
