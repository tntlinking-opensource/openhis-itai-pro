/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.charge.support;

import com.zxplat.plugin.peis.application.share.PeisCurrentPractitionerResolver;
import com.zxplat.domain.administration.chargeitem.query.OutpatientChargeQueueEncounterQuery;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.plugin.peis.domain.encounterext.queries.model.PeisEncounterExtModel;
import com.zxplat.plugin.peis.domain.encounterext.service.PeisEncounterExtQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 体检收银队列：PE scope 收敛 + 懒生成收费项 + 委托 {@link ChargeItemQueryService} 分页。
 */
@Component
@RequiredArgsConstructor
public class PhysicalExamChargeEncounterQueueLoader {

    private final ChargeItemQueryService chargeItemQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;
    private final PeisEncounterExtQueryService peisEncounterExtQueryService;
    private final PhysicalExamPlannedChargeEnsureSupport physicalExamPlannedChargeEnsureSupport;
    private final PeisCurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 分页返回存在 PLANNED/SETTLED 收费事实的 PE 就诊主键。
     *
     * @param query 队列入参（须含 excludedServiceTable）
     * @return 就诊主键页
     */
    public Page<Long> pageEncounterIdsForPhysicalExamCashierQueue(OutpatientChargeQueueEncounterQuery query) {
        LinkedHashSet<String> codes = new LinkedHashSet<>(4);
        codes.add(ChargeItemStatus.PLANNED.getCode());
        codes.add(ChargeItemStatus.SETTLED.getCode());
        List<Long> scope = this.resolvePeCashierEncounterScope(query);
        if (query.getPeGroupFlag() != null) {
            scope = peisEncounterExtQueryService.filterEncounterIdsByGroupFlag(scope, query.getPeGroupFlag());
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "当前会话操作人不能为空");
        // Pre：scope 内懒生成 PLANNED 收费项后再按收银事实分页
        physicalExamPlannedChargeEnsureSupport.ensureForEncounters(scope, operatorId);
        query.setResolvedEncounterScope(scope);
        return chargeItemQueryService.pageEncounterIdsForOutpatientChargeQueue(query, new ArrayList<>(codes));
    }

    /**
     * 解析 PE 收银可见就诊范围：日期窗基线 ∩ 关键字（busNo/体检流水/患者姓名）。
     *
     * @param query 含日期与 keyword
     * @return 去重 encounterId 列表
     */
    private List<Long> resolvePeCashierEncounterScope(OutpatientChargeQueueEncounterQuery query) {
        // Pre：日期窗内 PE 就诊基线（不按机构收窄）
        List<Long> baseScope = encounterQueryService.listPeEncounterIdsForCashierScope(
            null, query.getStartDate(), query.getEndDate());
        if (StringUtils.isEmpty(query.getKeyword())) {
            return baseScope;
        }
        LinkedHashSet<Long> keywordUnion = new LinkedHashSet<>();
        keywordUnion.addAll(encounterQueryService.filterPeEncounterIds(
            encounterQueryService.listEncounterIdsByBusNoKeyword(query.getKeyword())));
        PeisEncounterExtModel ext = peisEncounterExtQueryService.findByBusNo(query.getKeyword());
        if (ext != null && ext.getEncounterId() != null) {
            keywordUnion.add(ext.getEncounterId());
        }
        List<Long> patientIds = patientQueryService.listPatientIdsByEncounterListKeyword(query.getKeyword());
        keywordUnion.addAll(encounterQueryService.filterPeEncounterIds(
            encounterQueryService.listEncounterIdsByPatientIds(patientIds)));
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
