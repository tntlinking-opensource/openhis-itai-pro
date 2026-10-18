/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.outpatient.assembler;

import com.zxplat.plugin.chs.domain.share.enums.MedType;
import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.domain.administration.account.entity.Account;
import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.entity.EncounterDiagnosis;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.zxplat.domain.financial.contract.entity.Contract;
import com.zxplat.domain.financial.contract.service.ContractQueryService;
import com.zxplat.domain.share.constants.DoctorStationTcmConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.ContractCategory;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
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
 * 门诊收费项分组：按账户 + 就诊类型（med_type）+ 病种拆医保组，自费另组。
 *
 * <p>读 health-domain 收费项/账户/契约；纯内存分组，无 gateway 出站。
 * 同账户、同 med_type、同病种才并成一次 2206/2207；不同慢病须分次结算，避免费用行被写成第一个病种。
 * 未绑定诊断的医保行：若同批已绑诊断仅一种 med_type，则并入该 med_type 首个同账户组
 * （避免耗材空白诊断被当成普通门诊而迫使分次结算）。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class OutpatientInsuranceChargeGrouper {

    private final ChargeItemQueryService chargeItemQueryService;
    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;
    private final EncounterQueryService encounterQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    /** 代煎诊疗项目读侧：代煎行强制进自费组（防御历史错挂，不动 Account）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /**
     * 收集医保账户收费项主键（与 {@link #split} / 2204 同口径：自费账户与代煎不入列）。
     *
     * <p>供事前提醒门禁在出站 3101 前剔除自费明细；全自费时返回空列表由调用方 {@code skipped}。</p>
     *
     * @param encounterId   就诊主键
     * @param chargeItemIds 待筛选收费项主键（不可空）
     * @return 医保组内收费项 ID（保持组内出现顺序）；无医保项时为空列表
     */
    public List<Long> listInsuranceChargeItemIds(Long encounterId, List<Long> chargeItemIds) {
        OutpatientInsuranceChargeSplitResult split = this.split(encounterId, chargeItemIds);
        List<OutpatientInsuranceSettleGroupPlan> groups = split.getInsuranceGroups();
        // 无医保组 → 调用方按全自费跳过 3101
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        List<Long> insuranceIds = new ArrayList<>();
        for (OutpatientInsuranceSettleGroupPlan group : groups) {
            // 空组跳过，避免 NPE
            if (group == null || group.getChargeItems() == null) {
                continue;
            }
            for (ChargeItem item : group.getChargeItems()) {
                // 仅收集有效主键
                if (item != null && item.getId() != null) {
                    insuranceIds.add(item.getId());
                }
            }
        }
        return insuranceIds;
    }

    /**
     * 按就诊与收费项 ID 拆分为医保结算组与自费组。
     *
     * @param encounterId   就诊主键
     * @param chargeItemIds 待拆分收费项主键
     * @return 医保组 + 自费 rollup
     */
    public OutpatientInsuranceChargeSplitResult split(Long encounterId, List<Long> chargeItemIds) {
        // Gate：预结算批必须带收费项
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
        // 医保行无诊断：先搁置，待已绑诊断组确定唯一 med_type 后再并入
        List<ChargeItem> blankDxInsuranceItems = new ArrayList<>();

        Long decoctionActivityId = this.resolveDecoctionActivityIdOrNull();
        for (ChargeItem item : chargeItems) {
            // 代煎正常挂个人现金自费户；历史错挂医保户时仍强制进自费组，不进 2206
            if (this.isDecoctionChargeItem(item, decoctionActivityId)) {
                selfPayItems.add(item);
                continue;
            }
            Long accountId = item.getAccountId();
            // 缺 account_id 时回落就诊默认个人现金户
            if (accountId == null) {
                accountId = defaultSelfPayAccountId;
            }
            Account account = accountById.get(accountId);
            // 非医保账户（含个人现金）归入自费组
            if (!this.isInsuranceAccount(account)) {
                selfPayItems.add(item);
                continue;
            }
            Long primaryEncounterDiagnosisId = firstEncounterDiagnosisIdFromIds(item.getEncounterDiagnosisIds());
            // Pre：无诊断医保行稍后按同批唯一 med_type 继承，避免单独拆成普通门诊组
            if (primaryEncounterDiagnosisId == null) {
                blankDxInsuranceItems.add(item);
                continue;
            }
            String medType = this.resolveMedType(primaryEncounterDiagnosisId, medTypeByDxId);
            DiagnosisYbContext dxCtx = diagnosisContextByDxId.get(primaryEncounterDiagnosisId);
            String diseCodg = null;
            String diseName = null;
            if (dxCtx != null) {
                diseCodg = dxCtx.diseCodg();
                diseName = dxCtx.diseName();
            }
            this.addItemToInsuranceGroup(groupByKey, item, accountId, medType, diseCodg, diseName);
        }
        this.mergeBlankDxInsuranceItems(blankDxInsuranceItems, groupByKey, defaultSelfPayAccountId);

        List<OutpatientInsuranceSettleGroupPlan> insuranceGroups = new ArrayList<>(groupByKey.values());
        for (int index = 0; index < insuranceGroups.size(); index++) {
            insuranceGroups.get(index).setGroupIndex(index + 1);
        }

        OutpatientInsuranceChargeSplitResult result = new OutpatientInsuranceChargeSplitResult();
        result.setInsuranceGroups(insuranceGroups);
        result.setSelfPayItems(selfPayItems);
        if (!selfPayItems.isEmpty()) {
            // 自费 rollup 账户取收费行挂账（代煎个人现金户）；缺省回落就诊默认自费户
            result.setSelfPayAccountId(
                this.resolveSelfPayRollupAccountId(selfPayItems, defaultSelfPayAccountId));
        }
        return result;
    }

    /**
     * 解析自费 rollup 挂账账户：优先自费收费行自身 {@code accountId}，缺省再回落就诊默认个人现金户。
     *
     * @param selfPayItems             自费收费行
     * @param defaultSelfPayAccountId  就诊默认个人现金账户；可空
     * @return rollup 账户主键；均无法解析时 {@code null}
     */
    private Long resolveSelfPayRollupAccountId(
        List<ChargeItem> selfPayItems, Long defaultSelfPayAccountId) {
        // 无自费行时回落就诊默认个人现金户
        if (selfPayItems == null || selfPayItems.isEmpty()) {
            return defaultSelfPayAccountId;
        }
        // 取首个非空 accountId；多自费账户时 health 侧再按行分桶合并
        for (ChargeItem item : selfPayItems) {
            if (item != null && item.getAccountId() != null) {
                return item.getAccountId();
            }
        }
        return defaultSelfPayAccountId;
    }

    /**
     * 判断收费行是否为门诊代煎中药费用（账单侧识别，不依赖 Account.contract_no）。
     *
     * @param chargeItem           收费行
     * @param decoctionActivityId  代煎诊疗项目主键；未配置时为 null
     * @return 命中代煎锚点时 true
     */
    private boolean isDecoctionChargeItem(ChargeItem chargeItem, Long decoctionActivityId) {
        // 未配置代煎诊疗项目时静默不命中
        if (chargeItem == null || decoctionActivityId == null) {
            return false;
        }
        // 现行口径：product 锚定 ActivityDefinition
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(chargeItem.getProductTable())
            && decoctionActivityId.equals(chargeItem.getProductId())) {
            return true;
        }
        // 兼容历史错误锚点：service 直接指向 ActivityDefinition
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(chargeItem.getServiceTable())
            && decoctionActivityId.equals(chargeItem.getServiceId())) {
            return true;
        }
        return false;
    }

    /**
     * 解析租户代煎诊疗项目主键；未配置时返回 null（识别静默不命中）。
     *
     * @return 代煎 activity 主键；未配置时 null
     */
    private Long resolveDecoctionActivityIdOrNull() {
        try {
            return activityDefinitionQueryService.requireIdByExactName(
                DoctorStationTcmConstants.DECOCTION_ACTIVITY_NAME);
        } catch (RuntimeException ex) {
            // 租户未配置代煎诊疗项目时不阻断预结算拆分
            return null;
        }
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
     * 将医保收费行写入（或新建）分组。
     *
     * @param groupByKey 分组桶
     * @param item       收费行
     * @param accountId  挂账账户
     * @param medType    医疗类别
     * @param diseCodg   病种码；可空
     * @param diseName   病种名；可空
     */
    private void addItemToInsuranceGroup(
        Map<String, OutpatientInsuranceSettleGroupPlan> groupByKey,
        ChargeItem item,
        Long accountId,
        String medType,
        String diseCodg,
        String diseName) {
        // 普通门诊不按诊断拆组；慢特病/特病/特药才把病种码写进分组键，避免费用行被打成第一个病种
        String groupDiseCodg = diseCodg;
        String groupDiseName = diseName;
        if (!this.requiresPerDiseaseSettle(medType)) {
            groupDiseCodg = null;
            groupDiseName = null;
        }
        String partitionKey = this.partitionKey(accountId, medType, groupDiseCodg);
        OutpatientInsuranceSettleGroupPlan plan = groupByKey.get(partitionKey);
        if (plan == null) {
            plan = new OutpatientInsuranceSettleGroupPlan();
            plan.setAccountId(accountId);
            plan.setMedType(medType);
            plan.setDiseCodg(groupDiseCodg);
            plan.setDiseName(groupDiseName);
            plan.setChargeItems(new ArrayList<>());
            groupByKey.put(partitionKey, plan);
        } else if (this.requiresPerDiseaseSettle(medType) && isEmpty(plan.getDiseCodg()) && isNotEmpty(groupDiseCodg)) {
            // 慢特病首行无病种码时，用后续行补齐组级病种（不因此拆组）
            plan.setDiseCodg(groupDiseCodg);
            plan.setDiseName(groupDiseName);
        }
        plan.getChargeItems().add(item);
    }

    /**
     * 无诊断医保行：同批已绑诊断仅一种 med_type 时并入该类别首个同账户组；
     * 若尚无已绑诊断组则按普通门诊自立一组。
     *
     * @param blankDxItems            无诊断医保行
     * @param groupByKey              已按诊断分好的医保组
     * @param defaultSelfPayAccountId 缺省个人现金户
     */
    private void mergeBlankDxInsuranceItems(
        List<ChargeItem> blankDxItems,
        Map<String, OutpatientInsuranceSettleGroupPlan> groupByKey,
        Long defaultSelfPayAccountId) {
        if (blankDxItems == null || blankDxItems.isEmpty()) {
            return;
        }
        LinkedHashSet<String> diagnosedMedTypes = new LinkedHashSet<>();
        for (OutpatientInsuranceSettleGroupPlan plan : groupByKey.values()) {
            if (plan != null && isNotEmpty(plan.getMedType())) {
                diagnosedMedTypes.add(plan.getMedType().trim());
            }
        }
        String inheritedMedType = null;
        if (diagnosedMedTypes.size() == 1) {
            inheritedMedType = diagnosedMedTypes.iterator().next();
        } else if (diagnosedMedTypes.size() > 1) {
            // 防御：health 门禁应已拦截；仍勿把空白行拆成第三种类型
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "普通门诊与慢病等须分次结算（起付线），请每次只勾选同一就诊类型的明细");
        }
        for (ChargeItem item : blankDxItems) {
            Long accountId = item.getAccountId();
            if (accountId == null) {
                accountId = defaultSelfPayAccountId;
            }
            if (inheritedMedType == null) {
                // 整批均无诊断：保持原口径，按普通门诊分组
                this.addItemToInsuranceGroup(
                    groupByKey, item, accountId, MedType.GENERAL_OUTPATIENT.getCode(), null, null);
                continue;
            }
            OutpatientInsuranceSettleGroupPlan target =
                this.findMergeTargetPlan(groupByKey, accountId, inheritedMedType);
            if (target != null) {
                target.getChargeItems().add(item);
                continue;
            }
            // 同 med_type 无同账户组：克隆首个该 med_type 组的病种信息后新建
            OutpatientInsuranceSettleGroupPlan template =
                this.findFirstPlanByMedType(groupByKey, inheritedMedType);
            String diseCodg = null;
            String diseName = null;
            // 没有可克隆的组时病种留空，避免把空白诊断写成别的病种码
            if (template != null) {
                diseCodg = template.getDiseCodg();
                diseName = template.getDiseName();
            }
            this.addItemToInsuranceGroup(
                groupByKey, item, accountId, inheritedMedType, diseCodg, diseName);
        }
    }

    /**
     * 查找同账户、同 med_type 的首个医保组（用于无诊断行并入）。
     *
     * @param groupByKey 分组桶
     * @param accountId  账户
     * @param medType    医疗类别
     * @return 命中组；无则 null
     */
    private OutpatientInsuranceSettleGroupPlan findMergeTargetPlan(
        Map<String, OutpatientInsuranceSettleGroupPlan> groupByKey,
        Long accountId,
        String medType) {
        for (OutpatientInsuranceSettleGroupPlan plan : groupByKey.values()) {
            if (plan == null || isEmpty(plan.getMedType())) {
                continue;
            }
            if (!medType.equals(plan.getMedType().trim())) {
                continue;
            }
            boolean sameAccount = false;
            // 账户都空或主键相同才算同一挂账，才能并入同一病种组
            if (accountId == null) {
                sameAccount = plan.getAccountId() == null;
            } else {
                sameAccount = accountId.equals(plan.getAccountId());
            }
            if (sameAccount) {
                return plan;
            }
        }
        return null;
    }

    /**
     * 按 med_type 取首个医保组（复制病种码用）。
     *
     * @param groupByKey 分组桶
     * @param medType    医疗类别
     * @return 首个命中组；无则 null
     */
    private OutpatientInsuranceSettleGroupPlan findFirstPlanByMedType(
        Map<String, OutpatientInsuranceSettleGroupPlan> groupByKey,
        String medType) {
        for (OutpatientInsuranceSettleGroupPlan plan : groupByKey.values()) {
            if (plan != null && isNotEmpty(plan.getMedType()) && medType.equals(plan.getMedType().trim())) {
                return plan;
            }
        }
        return null;
    }

    /**
     * 是否按病种分次结算。
     *
     * <p>与 2203 须带 {@code dise_codg} 的医疗类别一致：14 / 140104 / 140201 / 15。
     * 普通门诊等同账户同类别合成一组，不把诊断码写进 2204。</p>
     *
     * @param medType 医疗类别
     * @return 慢特病、门诊慢病、门诊特病、特药为 true
     */
    private boolean requiresPerDiseaseSettle(String medType) {
        if (isEmpty(medType)) {
            return false;
        }
        String code = medType.trim();
        if (MedType.CHRONIC_SPECIAL_DISEASE_OUTPATIENT.getCode().equals(code)) {
            return true;
        }
        if (MedType.CHRONIC_DISEASE_OUTPATIENT.getCode().equals(code)) {
            return true;
        }
        if (MedType.SPECIAL_DISEASE_OUTPATIENT.getCode().equals(code)) {
            return true;
        }
        return MedType.SPECIAL_MEDICATION.getCode().equals(code);
    }

    /**
     * 生成医保结算分组键：账户 + 医疗类别 + 病种码。
     *
     * <p>仅慢特病等类别把病种码写入键。普通门诊病种码为空，同账户同类别并成一组。</p>
     *
     * @param accountId 账户 ID
     * @param medType   医疗类别
     * @param diseCodg  病种码；可空
     * @return 分组键字符串
     */
    private String partitionKey(Long accountId, String medType, String diseCodg) {
        StringBuilder key = new StringBuilder();
        if (accountId == null) {
            key.append("0");
        } else {
            key.append(accountId);
        }
        key.append('|');
        if (isNotEmpty(medType)) {
            key.append(medType.trim());
        }
        key.append('|');
        if (isNotEmpty(diseCodg)) {
            key.append(diseCodg.trim());
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
        Map<Long, com.zxplat.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel>
            definitionById = new HashMap<>();
        if (!definitionIdByDxId.isEmpty()) {
            List<com.zxplat.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel> definitions =
                conditionDefinitionQueryService.getConditionDefinitionListByIds(
                    new ArrayList<>(new LinkedHashSet<>(definitionIdByDxId.values())));
            for (com.zxplat.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel definition :
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
            com.zxplat.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel batchItem =
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
