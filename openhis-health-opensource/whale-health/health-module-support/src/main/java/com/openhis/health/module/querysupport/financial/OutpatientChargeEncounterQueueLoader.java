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

package com.openhis.health.module.querysupport.financial;

import com.openhis.health.domain.administration.chargeitem.query.OutpatientChargeQueueEncounterQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.share.constants.OutpatientRefundQueueTab;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 门诊收银/退费队列：跨聚合读编排（B.6.1.3），预解析 AMB 就诊收窄后委托 {@link ChargeItemQueryService}。
 *
 * <p>队列不按当前登录科室收窄，仅按日期窗、关键字等条件筛选。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientChargeEncounterQueueLoader {

    private final ChargeItemQueryService chargeItemQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;

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
        return this.pageWithResolvedScope(query, new ArrayList<>(codes));
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
        return this.pageWithResolvedScope(query, statusCodes);
    }

    private Page<Long> pageWithResolvedScope(OutpatientChargeQueueEncounterQuery query, List<String> statusCodes) {
        query.setResolvedEncounterScope(this.resolveOutpatientCashierEncounterScope(query));
        return this.chargeItemQueryService.pageEncounterIdsForOutpatientChargeQueue(query, statusCodes);
    }

    private List<Long> resolveOutpatientCashierEncounterScope(OutpatientChargeQueueEncounterQuery query) {
        List<Long> baseScope = this.encounterQueryService.listOutpatientAmbEncounterIdsForCashierScope(
            null, query.getStartDate(), query.getEndDate());
        if (StringUtils.isBlank(query.getKeyword())) {
            return baseScope;
        }
        LinkedHashSet<Long> keywordUnion = new LinkedHashSet<>();
        keywordUnion.addAll(this.encounterQueryService.filterOutpatientAmbEncounterIds(
            this.encounterQueryService.listEncounterIdsByBusNoKeyword(query.getKeyword())));
        List<Long> patientIds = this.patientQueryService.listPatientIdsByEncounterListKeyword(query.getKeyword());
        keywordUnion.addAll(this.encounterQueryService.filterOutpatientAmbEncounterIds(
            this.encounterQueryService.listEncounterIdsByPatientIds(patientIds)));
        LinkedHashSet<Long> baseSet = new LinkedHashSet<>(baseScope);
        List<Long> intersection = new ArrayList<>();
        for (Long encounterId : keywordUnion) {
            if (encounterId != null && baseSet.contains(encounterId)) {
                intersection.add(encounterId);
            }
        }
        return intersection;
    }
}
