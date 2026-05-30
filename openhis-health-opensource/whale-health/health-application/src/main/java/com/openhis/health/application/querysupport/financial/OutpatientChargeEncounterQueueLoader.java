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

package com.openhis.health.application.querysupport.financial;

import com.openhis.health.domain.administration.chargeitem.query.OutpatientChargeQueueEncounterQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.share.constants.OutpatientRefundQueueTab;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 门诊收银/退费队列：委托 {@link ChargeItemQueryService} 完成跨聚合分页读组装（B.6.1）。
 *
 * <p>机构收窄由 {@link ICurrentUser#getCurrentOrganizationUnitId()} 传入领域服务，禁止 Request 传 orgId（B.10.11）。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientChargeEncounterQueueLoader {

    private final ChargeItemQueryService chargeItemQueryService;
    private final ICurrentUser currentUser;

    /**
     * 门诊收银队列：分页返回存在待收费/已结算收费事实（排除挂号锚点）的就诊主键。
     *
     * @param query 须含 {@code excludedServiceTable}（挂号服务表名）
     * @return 就诊主键页
     */
    public Page<Long> pageEncounterIdsForOutpatientCashierQueue(OutpatientChargeQueueEncounterQuery query) {
        LinkedHashSet<String> codes = new LinkedHashSet<>(4);
        codes.add(ChargeItemStatus.PLANNED.getCode());
        codes.add(ChargeItemStatus.SETTLED.getCode());
        return this.chargeItemQueryService.pageEncounterIdsForOutpatientChargeQueue(
            query,
            new ArrayList<>(codes),
            this.currentUser.getCurrentOrganizationUnitId());
    }

    /**
     * 门诊退费队列：按页签收敛存在拟退/已退收费事实的就诊主键（AS-OP-FIN-003 BR-REF-001）。
     *
     * @param query        队列入参
     * @param queueTabCode {@link OutpatientRefundQueueTab}
     * @return 就诊主键页
     */
    public Page<Long> pageEncounterIdsForOutpatientRefundQueue(
        OutpatientChargeQueueEncounterQuery query,
        String queueTabCode) {
        List<String> statusCodes = new ArrayList<>(2);
        if (OutpatientRefundQueueTab.PENDING.equals(queueTabCode)) {
            statusCodes.add(ChargeItemStatus.REFUNDING.getCode());
        } else if (OutpatientRefundQueueTab.SETTLED.equals(queueTabCode)) {
            statusCodes.add(ChargeItemStatus.REFUNDED.getCode());
        } else {
            statusCodes.add(ChargeItemStatus.REFUNDING.getCode());
            statusCodes.add(ChargeItemStatus.REFUNDED.getCode());
        }
        return this.chargeItemQueryService.pageEncounterIdsForOutpatientChargeQueue(
            query,
            statusCodes,
            this.currentUser.getCurrentOrganizationUnitId());
    }
}
