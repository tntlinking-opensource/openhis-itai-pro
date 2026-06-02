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

package com.openhis.health.application.inpatient.financial.prepay.response;

import com.openhis.health.application.inpatient.financial.prepay.dto.PrepayPaymentRecordDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 预交金流水列表响应（IF-IP-PREP-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "预交金流水列表响应")
public class ListPrepayPaymentRecordsResponse {

    @Schema(description = "流水列表（按收费时间倒序）")
    private List<PrepayPaymentRecordDto> records;
}
