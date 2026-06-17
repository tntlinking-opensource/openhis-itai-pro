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

package com.openhis.health.application.inpatient.pharmacy.returnmedicine.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 住院确认退药响应（IF-IPR-04）。
 */
@Data
@Accessors(chain = true)
/** 住院确认退药响应。 */
@Schema(description = "住院确认退药响应")
public class ConfirmInpatientMedicineReturnResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "是否成功，固定 true")
    private Boolean success;

    /** 成功提示文案（EX-IPR-410）。 */
    @Schema(description = "成功提示文案")
    private String message;
}
