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

package com.openhis.health.application.outpatient.financial.charge.response;

import com.openhis.health.application.outpatient.financial.charge.dto.PendingChargeItemRowDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 加载待收费明细响应。
 */
@Data
@Accessors(chain = true)
public class LoadPendingChargeItemsResponse {
    /**
     * 就诊主键。
     */
    private Long encounterId;
    /**
     * 待收费项目明细列表。
     */
    private List<PendingChargeItemRowDto> items;
}
