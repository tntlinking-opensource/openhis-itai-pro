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

package com.openhis.health.domain.administration.chargeitem.command;

import lombok.Getter;
import lombok.Setter;

import java.util.Collection;

/**
 * 按上游请求锚点逻辑删除护士划价待结算收费明细（AS-IP-NUR-005 IF-IP-PRC-06 BR-IP-PRC-023/026）。
 */
@Getter
@Setter
public class VoidNursePricingChargeItemsByServiceAnchorsCommand {

    /** 操作人主键。 */
    private Long operatorId;

    /** 就诊标识。 */
    private Long encounterId;

    /** 上游业务表名（如 {@code wkf_service_request}、{@code wkf_device_request}）。 */
    private String serviceTable;

    /** 上游业务主键集合。 */
    private Collection<Long> serviceIds;
}
