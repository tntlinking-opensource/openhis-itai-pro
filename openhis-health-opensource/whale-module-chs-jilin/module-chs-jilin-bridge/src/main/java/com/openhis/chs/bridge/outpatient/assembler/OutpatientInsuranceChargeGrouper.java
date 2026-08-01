/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.assembler;

import com.openhis.chs.domain.share.enums.MedType;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.entity.EncounterDiagnosis;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ContractCategory;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊收费项分组：慢病多科室混合结算拆分为医保组与自费组。
 *
 * <p>读 health-domain 收费项/账户/契约；纯内存分组，无 gateway 出站。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class OutpatientInsuranceChargeGrouper {

    private static final Set<String> CHRONIC_MED_TYPES = Set.of(
        MedType.CHRONIC_SPECIAL_DISEASE_OUTPATIENT.getCode(),
        MedType.CHRONIC_DISEASE_OUTPATIENT.getCode(),
        MedType.SPECIAL_DISEASE_OUTPATIENT.getCode(),
        MedType.SPECIAL_MEDICATION.getCode());

    private final ChargeItemQueryService chargeItemQueryService;
    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final EncounterQueryService encounterQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    /** 按就诊与收费项 ID 拆分为医保结算组与自费组。 */
    public OutpatientInsuranceChargeSplitResult split(Long encounterId, List<Long> chargeItemIds) {
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费项不能为空");
        }
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(chargeItemIds);
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(query);
        if (chargeItems.size() != chargeItemIds.size()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费项不完整");
        }

        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterDxIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item.getAccountId() != null) {
                accountIds.add(item.getAccountId());
            }
            collectEncounterDiagnosisIdsFromIds(item.getEncounterDiagnosisIds(), encounterDxIds);
        }
        Map<Long, Account> accountById = accountQueryService.mapAccountsByIds(accountIds);
        Map<Long, String> medTypeByDxId = encounterQueryService.mapMedTypeByEncounterDiagnosisIds(encounterDxIds);
        Map<Long, DiagnosisYbContext> diagnosisContextByDxId = this.buildDiagnosisContextByDxId(encounterDxIds);

        List<ChargeItem> selfPayItems = new ArrayList<>();
        Map<String, OutpatientInsuranceSettleGroupPlan> groupByKey = new LinkedHashMap<>();

        for (ChargeItem item : chargeItems) {
            Long accountId = item.getAccountId();
            if (accountId == null) {
                accountId = defaultSelfPayAccountId;
            }
            Account account = accountById.get(accountId);
            if (!this.isInsuranceAccount(account)) {
                selfPayItems.add(item);
                continue;
            }
            Long primaryEncounterDiagnosisId = firstEncounterDiagnosisIdFromIds(item.getEncounterDiagnosisIds());
            String medType = this.resolveMedType(primaryEncounterDiagnosisId, medTypeByDxId);
            DiagnosisYbContext dxCtx = diagnosisContextByDxId.get(primaryEncounterDiagnosisId);
            String diseCodg = null;
            String diseName = null;
            if (dxCtx != null) {
                diseCodg = dxCtx.diseCodg();
                diseName = dxCtx.diseName();
            }
            String partitionKey = this.partitionKey(accountId, medType, diseCodg);
            OutpatientInsuranceSettleGroupPlan plan = groupByKey.get(partitionKey);
            if (plan == null) {
                plan = new OutpatientInsuranceSettleGroupPlan();
                plan.setAccountId(accountId);
                plan.setMedType(medType);
                plan.setDiseCodg(diseCodg);
                plan.setDiseName(diseName);
                plan.setChargeItems(new ArrayList<>());
                groupByKey.put(partitionKey, plan);
            }
            plan.getChargeItems().add(item);
        }

        List<OutpatientInsuranceSettleGroupPlan> insuranceGroups = new ArrayList<>(groupByKey.values());
        for (int index = 0; index < insuranceGroups.size(); index++) {
            insuranceGroups.get(index).setGroupIndex(index + 1);
        }

        OutpatientInsuranceChargeSplitResult result = new OutpatientInsuranceChargeSplitResult();
        result.setInsuranceGroups(insuranceGroups);
        result.setSelfPayItems(selfPayItems);
        if (!selfPayItems.isEmpty()) {
            result.setSelfPayAccountId(defaultSelfPayAccountId);
        }
        return result;
    }

    /**
     * 判断账户是否绑定医保费用性质。
     *
     * @param account 收费账户
     * @return 账户为医保契约时返回 {@code true}
     */
    private boolean isInsuranceAccount(Account account) {
        if (account == null || isEmpty(account.getContractNo())) {
            return false;
        }
        Contract contract = contractQueryService.findOptionalContractEntityByBusNo(account.getContractNo().trim())
            .orElse(null);
        if (contract == null) {
            return false;
        }
        if (Boolean.TRUE.equals(contract.getYbFlag())) {
            return true;
        }
        if (ContractCategory.WORKER_INSURANCE.getCode().equals(contract.getCategoryEnum())) {
            return false;
        }
        return ContractCategory.PROVINCIAL_INSURANCE.getCode().equals(contract.getCategoryEnum());
    }

    /**
     * 解析收费项对应的医疗类别，缺省为普通门诊。
     *
     * @param encounterDiagnosisId 就诊诊断 ID
     * @param medTypeByDxId        诊断 ID 到医疗类别的映射
     * @return 医疗类别代码
     */
    private String resolveMedType(Long encounterDiagnosisId, Map<Long, String> medTypeByDxId) {
        if (encounterDiagnosisId != null) {
            String medType = medTypeByDxId.get(encounterDiagnosisId);
            if (isNotEmpty(medType)) {
                return medType.trim();
            }
        }
        return MedType.GENERAL_OUTPATIENT.getCode();
    }

    /**
     * 生成医保结算分组键：账户 + 医疗类别（慢病附加病种编码）。
     *
     * @param accountId 账户 ID
     * @param medType   医疗类别
     * @param diseCodg  病种编码
     * @return 分组键字符串
     */
    private String partitionKey(Long accountId, String medType, String diseCodg) {
        StringBuilder key = new StringBuilder();
        if (accountId == null) {
            key.append("0");
        } else {
            key.append(accountId);
        }
        key.append('|').append(medType);
        if (CHRONIC_MED_TYPES.contains(medType) && isNotEmpty(diseCodg)) {
            key.append('|').append(diseCodg.trim());
        }
        return key.toString();
    }

    /**
     * 按就诊诊断 ID 批量构建医保病种上下文（编码与名称）。
     *
     * @param encounterDxIds 就诊诊断 ID 集合
     * @return 诊断 ID 到病种上下文的映射
     */
    private Map<Long, DiagnosisYbContext> buildDiagnosisContextByDxId(Set<Long> encounterDxIds) {
        if (encounterDxIds == null || encounterDxIds.isEmpty()) {
            return Map.of();
        }
        List<EncounterDiagnosis> rows = encounterQueryService.listEncounterDiagnosisEntitiesByIds(encounterDxIds);
        Map<Long, Long> definitionIdByDxId = new HashMap<>();
        Map<Long, String> descriptionByDxId = new HashMap<>();
        for (EncounterDiagnosis row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (isNotEmpty(row.getDescription())) {
                descriptionByDxId.put(row.getId(), row.getDescription().trim());
            }
            Long definitionId = row.getConditionDefinitionId();
            if (definitionId != null) {
                definitionIdByDxId.put(row.getId(), definitionId);
            }
        }
        Map<Long, com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel>
            definitionById = new HashMap<>();
        if (!definitionIdByDxId.isEmpty()) {
            List<com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel> definitions =
                conditionDefinitionQueryService.getConditionDefinitionListByIds(
                    new ArrayList<>(new LinkedHashSet<>(definitionIdByDxId.values())));
            for (com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel definition :
                definitions) {
                if (definition != null && definition.getId() != null) {
                    definitionById.put(definition.getId(), definition);
                }
            }
        }
        Map<Long, DiagnosisYbContext> out = new HashMap<>();
        for (Map.Entry<Long, Long> entry : definitionIdByDxId.entrySet()) {
            Long definitionId = entry.getValue();
            String diseName = descriptionByDxId.get(entry.getKey());
            com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel batchItem =
                definitionById.get(definitionId);
            String diseCodg = null;
            if (batchItem != null) {
                diseCodg = resolveNationalDiagCode(batchItem.getYbNo(), batchItem.getConditionCode());
                if (isEmpty(diseName) && isNotEmpty(batchItem.getName())) {
                    diseName = batchItem.getName().trim();
                }
            }
            out.put(entry.getKey(), new DiagnosisYbContext(diseCodg, diseName));
        }
        return out;
    }

    /**
     * 解析国标诊断编码：优先医保编码，其次条件编码。
     *
     * @param ybNo          医保诊断编码
     * @param conditionCode 条件定义编码
     * @return 国标诊断编码；均无值时返回 {@code null}
     */
    private static String resolveNationalDiagCode(String ybNo, String conditionCode) {
        if (isNotEmpty(ybNo)) {
            return ybNo.trim();
        }
        if (isNotEmpty(conditionCode)) {
            return conditionCode.trim();
        }
        return null;
    }

    /**
     * 从收费项绑定 ids 串 收集就诊诊断主键。
     *
     * @param idsText    有序 ids 串（首位为主诊断）
     * @param target 写入集合
     */
    private static void collectEncounterDiagnosisIdsFromIds(String idsText, Set<Long> target) {
        if (target == null || isEmpty(idsText)) {
            return;
        }
        String[] parts = idsText.split(",");
        for (String part : parts) {
            if (isEmpty(part)) {
                continue;
            }
            String trimmed = part.trim();
            if (isEmpty(trimmed)) {
                continue;
            }
            try {
                target.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 跳过非法段
            }
        }
    }

    /**
     * 取 ids 串中首个合法就诊诊断主键（有序；首位为主诊断）。
     *
     * @param idsText 有序 ids 串
     * @return 主诊断就诊诊断 ID；无合法值时返回 {@code null}
     */
    private static Long firstEncounterDiagnosisIdFromIds(String idsText) {
        if (isEmpty(idsText)) {
            return null;
        }
        String[] parts = idsText.split(",");
        for (String part : parts) {
            if (isEmpty(part)) {
                continue;
            }
            String trimmed = part.trim();
            if (isEmpty(trimmed)) {
                continue;
            }
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ignored) {
                // 跳过非法段
            }
        }
        return null;
    }

    private record DiagnosisYbContext(String diseCodg, String diseName) {
    }

    @Getter
    @Setter
    public static class OutpatientInsuranceSettleGroupPlan {
        private int groupIndex;
        private Long accountId;
        private String medType;
        private String diseCodg;
        private String diseName;
        private List<ChargeItem> chargeItems;
    }

    @Getter
    @Setter
    public static class OutpatientInsuranceChargeSplitResult {
        private List<OutpatientInsuranceSettleGroupPlan> insuranceGroups = List.of();
        private List<ChargeItem> selfPayItems = List.of();
        private Long selfPayAccountId;
    }
}
