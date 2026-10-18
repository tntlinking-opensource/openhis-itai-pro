/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.support;

import com.zxplat.domain.administration.chargeitem.query.OutpatientChargeQueueEncounterQuery;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.share.constants.OutpatientChargeQueueTab;
import com.zxplat.domain.share.constants.OutpatientRefundQueueTab;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
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
        return this.pageEncounterIdsForOutpatientCashierQueue(query, OutpatientChargeQueueTab.ALL);
    }

    /**
     * 门诊收银队列：按页签收敛收费状态后再分页（避免「先宽查分页再滤页签」导致条数漂移与全量回补超时）。
     *
     * <p>待收费页签仅收 {@link ChargeItemStatus#PLANNED}；已结算仅收 {@link ChargeItemStatus#SETTLED}；
     * 全部页签二者并集。应用层仍按 BR-OPC-012 打标做兜底过滤。</p>
     *
     * @param query        须含 {@code excludedServiceTable}
     * @param queueTabCode {@link OutpatientChargeQueueTab}
     * @return 就诊主键页
     */
    public Page<Long> pageEncounterIdsForOutpatientCashierQueue(
        OutpatientChargeQueueEncounterQuery query,
        String queueTabCode) {
        List<String> statusCodes = new ArrayList<>(2);
        if (OutpatientChargeQueueTab.PENDING.equals(queueTabCode)) {
            // 待收费页签：仅 PLANNED，避免已结就诊挤占分页窗口
            statusCodes.add(ChargeItemStatus.PLANNED.getCode());
        } else if (OutpatientChargeQueueTab.SETTLED.equals(queueTabCode)) {
            // 已结算页签：仅 SETTLED
            statusCodes.add(ChargeItemStatus.SETTLED.getCode());
        } else {
            // 全部/缺省：待收与已结并集
            statusCodes.add(ChargeItemStatus.PLANNED.getCode());
            statusCodes.add(ChargeItemStatus.SETTLED.getCode());
        }
        return this.pageWithResolvedScope(query, statusCodes);
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
            // 待退页签：仅 REFUNDING 收费事实
            statusCodes.add(ChargeItemStatus.REFUNDING.getCode());
        } else if (OutpatientRefundQueueTab.SETTLED.equals(queueTabCode)) {
            // 已退页签：仅 REFUNDED 收费事实
            statusCodes.add(ChargeItemStatus.REFUNDED.getCode());
        } else {
            // 默认/全部：拟退与已退并集
            statusCodes.add(ChargeItemStatus.REFUNDING.getCode());
            statusCodes.add(ChargeItemStatus.REFUNDED.getCode());
        }
        return this.pageWithResolvedScope(query, statusCodes);
    }

    /**
     * 解析就诊范围后分页查询门诊收费队列 encounterId。
     *
     * @param query        队列查询（会被写入 resolvedEncounterScope）
     * @param statusCodes  charge_item 状态过滤
     * @return encounterId 分页
     */
    private Page<Long> pageWithResolvedScope(OutpatientChargeQueueEncounterQuery query, List<String> statusCodes) {
        query.setResolvedEncounterScope(this.resolveOutpatientCashierEncounterScope(query));
        return this.chargeItemQueryService.pageEncounterIdsForOutpatientChargeQueue(query, statusCodes);
    }

    /**
     * 门诊收银台可见就诊范围：日期窗 AMB 基线 ∩ 关键字（busNo/患者）并集。
     *
     * @param query 含日期与 keyword
     * @return 去重 encounterId 列表
     */
    private List<Long> resolveOutpatientCashierEncounterScope(OutpatientChargeQueueEncounterQuery query) {
        // Pre：日期窗内 AMB 就诊基线（B.6.4 Encounter QS）
        List<Long> baseScope = this.encounterQueryService.listOutpatientAmbEncounterIdsForCashierScope(
            null, query.getStartDate(), query.getEndDate());
        if (StringUtils.isEmpty(query.getKeyword())) {
            return baseScope;
        }
        // 关键字：门诊号 busNo 与患者姓名/拼音并集后再与 baseScope 求交
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
