/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.settlement.support;

import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.entity.EncounterDiagnosis;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ContractCategory;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊收费项分组器：慢病多科室混合结算的核心拆分逻辑。
 *
 * <p>分组键 {@code accountId | med_type [| dise_codg]}：</p>
 * <ul>
 *   <li>同一 {@code adm_account}（费用账户/费用性质）且 med_type 相同 → 同一 2206 批次</li>
 *   <li>慢病/特病 med_type（14/1401/1402/9901）额外按 {@code dise_codg} 拆分，保证同批病种一致</li>
 *   <li>非医保账户（自费 contract）→ {@code selfPayItems}，不调平台，仅汇总金额</li>
 * </ul>
 *
 * <p>设计真源：{@code whale-doc/chs-jilin/20-场景-慢病多科室混合结算-数据时序.md}</p>
 *
 * <p>下游：{@link com.openhis.health.integration.insurance.settlement.ChsOutpatientSettlementClientImpl#preSettleBatch}
 * 逐组调用 preSettle，失败时 2205 补偿。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class OutpatientInsuranceChargeGrouper {

    /**
     * 须在 partitionKey 中附加 dise_codg 的 med_type 集合（平台要求同 chrg_bchno 病种一致）。
     */
    private static final Set<String> CHRONIC_MED_TYPES = Set.of(
        YbMedType.CHRONIC_SPECIAL_DISEASE_OUTPATIENT.getCode(),
        YbMedType.CHRONIC_DISEASE_OUTPATIENT.getCode(),
        YbMedType.SPECIAL_DISEASE_OUTPATIENT.getCode(),
        YbMedType.SPECIAL_MEDICATION.getCode());

    private final ChargeItemQueryService chargeItemQueryService;
    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final EncounterQueryService encounterQueryService;
    private final ConditionQueryService conditionQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    /**
     * 将一次收银的全部 chargeItem 拆分为「医保组」与「自费项」。
     *
     * @param encounterId 就诊 id（用于默认自费账户）
     * @param chargeItemIds 待结算收费项 id 列表
     * @return 医保分组计划 + 自费项 + 自费账户 id
     * @throws BusinessException 收费项为空或不完整
     */
    public OutpatientInsuranceChargeSplitResult split(Long encounterId, List<Long> chargeItemIds) {
        // Gate：无收费项不可预结算
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费项不能为空");
        }
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(chargeItemIds);
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(query);
        if (chargeItems.size() != chargeItemIds.size()) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费项不完整");
        }

        // Pre：批量加载账户、就诊诊断 med_type、病种编码
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterDxIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item.getAccountId() != null) {
                accountIds.add(item.getAccountId());
            }
            if (item.getEncounterDiagnosisId() != null) {
                encounterDxIds.add(item.getEncounterDiagnosisId());
            }
        }
        Map<Long, Account> accountById = accountQueryService.mapAccountsByIds(accountIds);
        Map<Long, String> medTypeByDxId = encounterQueryService.mapMedTypeByEncounterDiagnosisIds(encounterDxIds);
        Map<Long, DiagnosisYbContext> diagnosisContextByDxId = this.buildDiagnosisContextByDxId(encounterDxIds);

        // Cmd：逐 chargeItem 归入医保组或自费列表
        List<ChargeItem> selfPayItems = new ArrayList<>();
        Map<String, OutpatientInsuranceSettleGroupPlan> groupByKey = new LinkedHashMap<>();

        for (ChargeItem item : chargeItems) {
            Long accountId = item.getAccountId();
            // 无 account 时挂默认自费账户，后续 isInsuranceAccount 判定
            if (accountId == null) {
                accountId = defaultSelfPayAccountId;
            }
            Account account = accountById.get(accountId);
            // 非医保费用性质 → 自费汇总，不走 2204
            if (!this.isInsuranceAccount(account)) {
                selfPayItems.add(item);
                continue;
            }
            String medType = this.resolveMedType(item.getEncounterDiagnosisId(), medTypeByDxId);
            DiagnosisYbContext dxCtx = diagnosisContextByDxId.get(item.getEncounterDiagnosisId());
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

        // Asm：赋 groupIndex（1-based），组装结果
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
     * 账户关联的费用性质是否为医保（yb_flag 或省医保 category）。
     */
    private boolean isInsuranceAccount(Account account) {
        if (account == null || StringUtils.isBlank(account.getContractNo())) {
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
        return ContractCategory.PROVINCIAL_INSURANCE.getCode().equals(contract.getCategoryEnum());
    }

    /**
     * 从就诊诊断取 med_type；无诊断或未配置时默认普通门诊 11。
     */
    private String resolveMedType(Long encounterDiagnosisId, Map<Long, String> medTypeByDxId) {
        if (encounterDiagnosisId != null) {
            String medType = medTypeByDxId.get(encounterDiagnosisId);
            if (StringUtils.isNotBlank(medType)) {
                return medType.trim();
            }
        }
        return YbMedType.GENERAL_OUTPATIENT.getCode();
    }

    /**
     * 生成分组键：accountId|medType[|diseCodg]。
     *
     * <p>慢病 med_type 且 dise_codg 非空时追加病种，防止不同慢病混在同一 chrg_bchno。</p>
     */
    private String partitionKey(Long accountId, String medType, String diseCodg) {
        StringBuilder key = new StringBuilder();
        if (accountId == null) {
            key.append("0");
        } else {
            key.append(accountId);
        }
        key.append('|').append(medType);
        if (CHRONIC_MED_TYPES.contains(medType) && StringUtils.isNotBlank(diseCodg)) {
            key.append('|').append(diseCodg.trim());
        }
        return key.toString();
    }

    /**
     * 批量解析 encounterDiagnosisId → 病种编码/名称（2203/2204 dise_codg 来源）。
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
            if (StringUtils.isNotBlank(row.getDescription())) {
                descriptionByDxId.put(row.getId(), row.getDescription().trim());
            }
            Long definitionId = conditionQueryService.findDefinitionIdByConditionId(row.getConditionId());
            if (definitionId != null) {
                definitionIdByDxId.put(row.getId(), definitionId);
            }
        }
        // Pre：批量加载目录（含 yb_no），避免按诊断逐条 IN 查询
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
                diseCodg = InsuranceDiagnosisCodeSupport.resolveNationalDiagCode(
                    batchItem.getYbNo(), batchItem.getConditionCode());
                if (StringUtils.isBlank(diseName) && StringUtils.isNotBlank(batchItem.getName())) {
                    diseName = batchItem.getName().trim();
                }
            }
            out.put(entry.getKey(), new DiagnosisYbContext(diseCodg, diseName));
        }
        return out;
    }

    /** 就诊诊断维度的病种编码与名称。 */
    private record DiagnosisYbContext(String diseCodg, String diseName) {
    }

    /** 单组医保预结算计划（对应一次 2203→2204→2206）。 */
    @Getter
    @Setter
    public static class OutpatientInsuranceSettleGroupPlan {
        /** 组序号，从 1 开始，供收银展示。 */
        private int groupIndex;
        /** 费用账户 id（决定 contractBusNo）。 */
        private Long accountId;
        /** 本组医疗类别（11/14/1401…）。 */
        private String medType;
        /** 慢病病种编码（dise_codg）。 */
        private String diseCodg;
        /** 慢病病种名称。 */
        private String diseName;
        /** 本组包含的收费项。 */
        private List<ChargeItem> chargeItems;
    }

    /** split 输出：医保组 + 自费项。 */
    @Getter
    @Setter
    public static class OutpatientInsuranceChargeSplitResult {
        /** 须分别 preSettle 的医保组；空表示全部自费。 */
        private List<OutpatientInsuranceSettleGroupPlan> insuranceGroups = List.of();
        /** 不走平台的自费收费项。 */
        private List<ChargeItem> selfPayItems = List.of();
        /** 自费项归属账户（收银写 fin 用）。 */
        private Long selfPayAccountId;
    }
}
