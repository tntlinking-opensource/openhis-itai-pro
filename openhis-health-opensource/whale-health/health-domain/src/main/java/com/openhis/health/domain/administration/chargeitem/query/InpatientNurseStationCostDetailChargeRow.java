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

package com.openhis.health.domain.administration.chargeitem.query;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * 住院护士站费用明细查询行（DM-ADM-011 §6.2.15；AS-IP-NUR-006 IF-IP-CDT-02）。
 *
 * <p>携带已解析的<strong>业务时间</strong>（划价账单时刻或净剩 Procedure 执行时刻），供应用层明细排序与展示，避免重复解析。</p>
 */
@Getter
@RequiredArgsConstructor
public class InpatientNurseStationCostDetailChargeRow {

    /** 收费明细事实行。 */
    private final ChargeItem chargeItem;

    /** 业务时间（用于日期区间过滤与明细倒序）。 */
    private final LocalDateTime businessDateTime;
}
