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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.mapper;

import lombok.Getter;
import lombok.Setter;

/**
 * 补费父锚点解析结果（写入 Request.based_on_table / based_on_id）。
 *
 * <p>由 {@link NurseStationInpatientBillingMapper#resolveBasedOn} 根据 orderType 映射物理表。</p>
 */
@Getter
@Setter
public class NurseStationPricingBasedOnPair {

    /** 上级医嘱来源表。 */
    private String basedOnTable;

    /** 上级医嘱来源主键。 */
    private Long basedOnId;
}
