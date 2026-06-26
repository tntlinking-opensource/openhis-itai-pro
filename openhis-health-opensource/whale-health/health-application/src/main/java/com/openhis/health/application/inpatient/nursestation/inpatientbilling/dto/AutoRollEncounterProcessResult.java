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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-IP-ARG-08：单 encounter 滚费处理计数。
 */
@Getter
@Setter
public class AutoRollEncounterProcessResult {

    /** 本就诊是否至少成功落账 1 条 ChargeItem。 */
    private boolean processedEncounter;

    /** 新建 ChargeItem 条数。 */
    private int createdChargeItemCount;

    /** 幂等/边界跳过条数。 */
    private int skippedCount;

    /** 单条落账失败条数。 */
    private int failedCount;
}
