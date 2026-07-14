/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.workflow;

import com.openhis.chs.domain.outpatient.result.OpPlatformRegistrationResult;
import com.openhis.chs.domain.outpatient.queries.model.OpPlatformRegistrationQueryModel;
import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.command.OpRevokePlatformRegistrationCommand;
import com.openhis.chs.domain.outpatient.result.OpRevokePlatformRegistrationResult;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.service.YbClinicPreSettleQueryService;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.bridge.outpatient.assembler.OutpatientSettleCommandAssembler;
import com.openhis.chs.bridge.outpatient.invoker.Op2201Invoker;
import com.openhis.chs.bridge.outpatient.invoker.Op2202Invoker;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 【2201→2202】门诊平台挂号登记编排。
 *
 * <p>出站由 {@link Op2201Invoker}、{@link Op2202Invoker} 完成；本类不调 gateway。
 * 读 yb_encounter_platform_map、yb_clinic_pre_settle 组装快照。</p>
 */
@Component
@RequiredArgsConstructor
public class OpPlatformRegistrationOrchestrator {

    private final Op2201Invoker op2201Invoker;
    private final Op2202Invoker op2202Invoker;
    private final YbEncounterPlatformMapQueryService encounterPlatformMapQueryService;
    private final YbClinicPreSettleQueryService clinicPreSettleQueryService;

    /** 确保就诊已在平台完成 2201 挂号登记，返回 mdtrt_id。 */
    public OpPlatformRegistrationResult ensureRegistration(OpPreSettleCommand context) {
        OutpatientSettleCommandAssembler.validateRegistrationContext(context);
        OutpatientSettleCommandAssembler.requireContractConfig(context.getContractBusNo());
        InvokeContext invokeContext = OutpatientSettleCommandAssembler.buildInvokeContext(context);
        Long tenantId = OutpatientSettleCommandAssembler.resolveTenantId(context.getTenantId());
        String mdtrtId = op2201Invoker.resolveOrRegister(
            context.getContractBusNo(), context, invokeContext, tenantId);
        return new OpPlatformRegistrationResult().setMdtrtId(mdtrtId);
    }

    /** 撤销平台挂号登记（2202），本地映射软删。 */
    public OpRevokePlatformRegistrationResult revokeRegistration(OpRevokePlatformRegistrationCommand command) {
        return op2202Invoker.invoke(command);
    }

    /** 批量查询就诊的平台挂号与预结算快照。 */
    public List<OpPlatformRegistrationQueryModel> listSnapshots(
            List<Long> encounterIds,
            Map<Long, Long> paymentDraftIdByEncounter) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return List.of();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return List.of();
        }
        List<YbEncounterPlatformMap> maps =
            encounterPlatformMapQueryService.listByEncounterIds(unique, tenantId);
        Map<Long, YbEncounterPlatformMap> mapByEncounter = new HashMap<>();
        for (YbEncounterPlatformMap row : maps) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            mapByEncounter.putIfAbsent(row.getEncounterId(), row);
        }
        Map<Long, YbClinicPreSettle> preByEncounter = new HashMap<>();
        List<YbClinicPreSettle> preRows = clinicPreSettleQueryService.listByEncounterIds(unique, tenantId);
        for (YbClinicPreSettle pre : preRows) {
            if (pre == null || pre.getEncounterId() == null) {
                continue;
            }
            if (preSettleStatusRank(pre.getStatus()) <= 0) {
                continue;
            }
            Long encounterId = pre.getEncounterId();
            YbClinicPreSettle current = preByEncounter.get(encounterId);
            if (current == null || preSettleStatusRank(pre.getStatus()) > preSettleStatusRank(current.getStatus())) {
                preByEncounter.put(encounterId, pre);
            }
        }
        if (paymentDraftIdByEncounter != null) {
            for (Long encounterId : unique) {
                Long draftId = paymentDraftIdByEncounter.get(encounterId);
                if (draftId == null) {
                    continue;
                }
                clinicPreSettleQueryService
                    .findOptionalLatestByPaymentDraftId(draftId, tenantId)
                    .ifPresent(pre -> {
                        if (pre.getEncounterId() != null && preSettleStatusRank(pre.getStatus()) > 0) {
                            preByEncounter.put(pre.getEncounterId(), pre);
                        }
                    });
            }
        }
        List<OpPlatformRegistrationQueryModel> result = new ArrayList<>();
        for (Long encounterId : unique) {
            OpPlatformRegistrationQueryModel snap = new OpPlatformRegistrationQueryModel();
            snap.setEncounterId(encounterId);
            YbEncounterPlatformMap mapped = mapByEncounter.get(encounterId);
            if (mapped != null) {
                snap.setContractId(mapped.getContractId());
                snap.setMdtrtId(mapped.getMdtrtId());
                snap.setPlatformRegistered(Boolean.TRUE);
            } else {
                snap.setPlatformRegistered(Boolean.FALSE);
            }
            YbClinicPreSettle pre = preByEncounter.get(encounterId);
            if (pre != null && preSettleStatusRank(pre.getStatus()) > 0) {
                snap.setInsurancePreSettled(Boolean.TRUE);
                snap.setInsuplcAdmdvs(pre.getInsuplcAdmdvs());
                snap.setYbFundPaySumamt(pre.getFundPaySumamt());
                snap.setYbAcctPay(pre.getAcctPay());
                snap.setYbPsnCashPay(pre.getPsnCashPay());
            } else {
                snap.setInsurancePreSettled(Boolean.FALSE);
            }
            result.add(snap);
        }
        return result;
    }

    /**
     * 预结算状态优先级排序：ACTIVE > SETTLED > 其他。
     *
     * @param status 预结算状态
     * @return 优先级数值，越大越优先
     */
    private static int preSettleStatusRank(String status) {
        if (YbClinicPreSettle.STATUS_ACTIVE.equals(status)) {
            return 2;
        }
        if (YbClinicPreSettle.STATUS_SETTLED.equals(status)) {
            return 1;
        }
        return 0;
    }
}
