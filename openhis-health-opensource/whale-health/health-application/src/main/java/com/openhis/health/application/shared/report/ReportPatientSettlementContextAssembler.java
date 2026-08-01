/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.report;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.health.domain.workflow.devicedispense.query.ConsumableUsageReportPageQuery;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报表患者结算上下文装配：费用性质（优先账户 → fin_contract）+ medType（主诊断优先）。
 *
 * <p>口径对齐挂号明细/患者卡片：有医保账户优先其合同；medType 取就诊诊断主诊，否则首条非空。
 * 费用性质筛选下拉 {@code value=bus_no}，与日结 expenseProperty 一致。</p>
 */
@Component
@RequiredArgsConstructor
public class ReportPatientSettlementContextAssembler {

    /** 就诊优先费用性质合同号。 */
    private final AccountQueryService accountQueryService;

    /** 合同主数据读侧。 */
    private final ContractQueryService contractQueryService;

    /** 就诊诊断 med_type 读侧。 */
    private final EncounterQueryService encounterQueryService;

    /**
     * 批量装配就诊 → 费用性质 + medType 展示上下文。
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊主键 → 上下文；无可用字段时仍可含空壳行（调用方按字段判空）
     */
    public Map<Long, PatientSettlementContextRow> mapContextsByEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        // Loop：去重有效就诊主键
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                unique.add(encounterId);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        // Pre：优先合同号 + 合同名 + 主诊 med_type
        Map<Long, String> contractNoByEncounter =
            this.accountQueryService.mapPreferredContractNoByEncounterIds(unique);
        Map<String, String> contractNameByBusNo = this.loadContractNames(contractNoByEncounter);
        Map<Long, String> medTypeByEncounter =
            this.encounterQueryService.mapPreferredMedTypeByEncounterIds(unique);
        Map<Long, PatientSettlementContextRow> out = new HashMap<>(unique.size() * 2);
        // Loop：逐就诊写入费用性质与医疗类别
        for (Long encounterId : unique) {
            PatientSettlementContextRow row = new PatientSettlementContextRow();
            String contractNo = contractNoByEncounter.get(encounterId);
            if (StringUtils.isNotEmpty(contractNo)) {
                row.setFeeNatureContractNo(contractNo);
                row.setFeeNatureDisplay(contractNameByBusNo.get(contractNo));
            }
            String medType = medTypeByEncounter.get(encounterId);
            if (StringUtils.isNotEmpty(medType)) {
                row.setMedType(medType);
                row.setMedTypeDisplay(this.resolveMedTypeDisplay(medType));
            }
            out.put(encounterId, row);
        }
        return out;
    }

    /**
     * 按优先费用性质合同号解析就诊主键（空筛选返回 null 表示不收窄）。
     *
     * @param feeNatureContractNo {@code fin_contract.bus_no}；空则不筛选
     * @return 匹配就诊主键；筛选有值但无命中时为空列表；未筛选时 {@code null}
     */
    public List<Long> resolveEncounterIdsByFeeNatureContractNo(String feeNatureContractNo) {
        String normalized = trimToNull(feeNatureContractNo);
        // 未选费用性质：不按合同收窄
        if (normalized == null) {
            return null;
        }
        return this.accountQueryService.listEncounterIdsByPreferredContractNo(normalized);
    }

    /**
     * 费用性质下拉：启用中合同 {@code bus_no → name}（与日结同口径）。
     *
     * @return OptionDto 列表；永不为 null
     */
    public List<OptionDto> loadFeeNatureOptions() {
        List<OptionDto> options = new ArrayList<>();
        List<ContractListItemQueryModel> contracts = this.contractQueryService.listAllContracts();
        // Loop：仅启用且 busNo/name 齐全的合同入下拉
        for (ContractListItemQueryModel contract : contracts) {
            if (contract == null) {
                continue;
            }
            if (Boolean.FALSE.equals(contract.getActiveFlag())) {
                continue;
            }
            String busNo = trimToNull(contract.getBusNo());
            String name = trimToNull(contract.getName());
            if (busNo == null || name == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            if (contract.getDisplayOrder() != null) {
                option.setSort(contract.getDisplayOrder().longValue());
            }
            option.setValue(busNo);
            option.setLabel(name);
            options.add(option);
        }
        return options;
    }

    /**
     * 将费用性质筛选结果与已有就诊 scope 求交。
     *
     * @param existingScope             既有就诊收窄；{@code null} 表示尚未按就诊收窄
     * @param feeNatureEncounterIds     费用性质命中就诊；{@code null} 表示未筛选
     * @return 求交后的就诊列表；费用性质无命中时为空列表；双方皆 null 时返回 null
     */
    public List<Long> intersectEncounterScopes(
        List<Long> existingScope,
        List<Long> feeNatureEncounterIds) {
        // 未按费用性质筛选：原样返回既有 scope
        if (feeNatureEncounterIds == null) {
            return existingScope;
        }
        // 费用性质无命中：强制空结果，避免全表
        if (feeNatureEncounterIds.isEmpty()) {
            return List.of();
        }
        // 尚无其它就诊收窄：直接采用费用性质命中集
        if (existingScope == null) {
            return new ArrayList<>(feeNatureEncounterIds);
        }
        // 既有 scope 已空：保持空
        if (existingScope.isEmpty()) {
            return List.of();
        }
        Set<Long> feeNatureSet = new HashSet<>(feeNatureEncounterIds);
        List<Long> intersected = new ArrayList<>();
        // Loop：保留同时落在两集合内的就诊
        for (Long encounterId : existingScope) {
            if (encounterId != null && feeNatureSet.contains(encounterId)) {
                intersected.add(encounterId);
            }
        }
        return intersected;
    }

    /**
     * 发药报表患者侧：费用性质筛选与已有就诊 scope 求交。
     *
     * @param query                 发放记录 Query；原地写入 resolved 就诊字段
     * @param feeNatureContractNo   {@code fin_contract.bus_no}；空则不筛选
     */
    public void applyFeeNatureFilterToMedicationDispenseRecordQuery(
        MedicationDispenseRecordPageQuery query,
        String feeNatureContractNo) {
        if (query == null) {
            return;
        }
        List<Long> feeNatureEncounterIds = this.resolveEncounterIdsByFeeNatureContractNo(feeNatureContractNo);
        List<Long> intersectedForScene = this.intersectEncounterScopes(
            query.getResolvedEncounterIdsForScene(),
            feeNatureEncounterIds);
        // 费用性质无命中：强制空 scope，领域侧拒查
        if (intersectedForScene != null && intersectedForScene.isEmpty()) {
            query.setResolvedEncounterIdsForScene(List.of());
            query.setResolvedEncounterIdsForPatientKeyword(List.of());
            return;
        }
        if (intersectedForScene != null) {
            query.setResolvedEncounterIdsForScene(intersectedForScene);
        }
        List<Long> intersectedForPatient = this.intersectEncounterScopes(
            query.getResolvedEncounterIdsForPatientKeyword(),
            feeNatureEncounterIds);
        if (intersectedForPatient != null && intersectedForPatient.isEmpty()) {
            query.setResolvedEncounterIdsForPatientKeyword(List.of());
            return;
        }
        if (intersectedForPatient != null) {
            query.setResolvedEncounterIdsForPatientKeyword(intersectedForPatient);
        }
    }

    /**
     * 耗材使用报表患者侧：费用性质筛选与 {@code resolvedEncounterScope} 求交。
     *
     * @param query                 耗材 Query；原地写入 resolvedEncounterScope
     * @param feeNatureContractNo   {@code fin_contract.bus_no}；空则不筛选
     */
    public void applyFeeNatureFilterToConsumableUsageReportPageQuery(
        ConsumableUsageReportPageQuery query,
        String feeNatureContractNo) {
        if (query == null) {
            return;
        }
        List<Long> feeNatureEncounterIds = this.resolveEncounterIdsByFeeNatureContractNo(feeNatureContractNo);
        List<Long> intersectedScope = this.intersectEncounterScopes(
            query.getResolvedEncounterScope(),
            feeNatureEncounterIds);
        // 费用性质无命中：强制空 scope
        if (intersectedScope != null && intersectedScope.isEmpty()) {
            query.setResolvedEncounterScope(List.of());
            return;
        }
        if (intersectedScope != null) {
            query.setResolvedEncounterScope(intersectedScope);
        }
    }

    /**
     * 批量解析合同业务号 → 名称。
     *
     * @param contractNoByEncounter 就诊 → 合同号
     * @return 合同号 → 名称
     */
    private Map<String, String> loadContractNames(Map<Long, String> contractNoByEncounter) {
        if (contractNoByEncounter == null || contractNoByEncounter.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> busNos = new HashSet<>();
        // Loop：收集非空合同号
        for (String busNo : contractNoByEncounter.values()) {
            String normalized = trimToNull(busNo);
            if (normalized != null) {
                busNos.add(normalized);
            }
        }
        if (busNos.isEmpty()) {
            return Collections.emptyMap();
        }
        return this.contractQueryService.mapContractNameByBusNoIn(busNos);
    }

    /**
     * med_type code → 展示文案；未知码回落原 code。
     *
     * @param medTypeCode {@code YbMedType#getCode()}
     * @return 展示文案
     */
    private String resolveMedTypeDisplay(String medTypeCode) {
        YbMedType medType = EnumUtil.fromCode(YbMedType.class, medTypeCode);
        if (medType == null) {
            return medTypeCode;
        }
        String display = trimToNull(medType.getDisplay());
        if (display == null) {
            return medTypeCode;
        }
        return display;
    }
}
