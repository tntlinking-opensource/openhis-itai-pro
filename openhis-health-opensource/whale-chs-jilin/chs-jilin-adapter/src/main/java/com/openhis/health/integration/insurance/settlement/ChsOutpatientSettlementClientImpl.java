/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.settlement;

import com.openhis.chs.domain.outpatient.dto.OpConfirmSettleCommand;
import com.openhis.chs.domain.outpatient.dto.OpPlatformRegistrationResult;
import com.openhis.chs.domain.outpatient.dto.OpConfirmSettleResult;
import com.openhis.chs.domain.outpatient.dto.OpPreSettleResult;
import com.openhis.chs.domain.outpatient.dto.OpRevokePlatformRegistrationCommand;
import com.openhis.chs.domain.outpatient.dto.OpRevokePlatformRegistrationResult;
import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.repository.YbEncounterPlatformMapRepository;
import com.openhis.health.integration.insurance.support.InsuranceContractResolver;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceRevokeRegistrationCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsurancePlatformRegistrationSnapshot;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceRevokeRegistrationResult;
import com.openhis.chs.domain.outpatient.dto.OpRevokeFeeDetailCommand;
import com.openhis.chs.domain.outpatient.dto.OpRevokeFeeDetailResult;
import com.openhis.chs.domain.outpatient.dto.OpRevokeSettleCommand;
import com.openhis.chs.domain.outpatient.dto.OpRevokeSettleResult;
import com.openhis.chs.domain.outpatient.dto.OpSettleContext;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.settlement.dto.SettlementCorrectionCommand;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.outpatient.facade.ChsOutpatientSettleOperations;
import com.openhis.chs.domain.outpatient.repository.YbClinicPreSettleRepository;
import com.openhis.chs.domain.settlement.repository.YbSettlementRecordRepository;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.utils.InsuranceContractCategorySupport;
import com.openhis.health.integration.insurance.settlement.support.OutpatientInsuranceChargeGrouper;
import com.openhis.health.integration.insurance.settlement.support.OutpatientInsuranceChargeGrouper.OutpatientInsuranceChargeSplitResult;
import com.openhis.health.integration.insurance.settlement.support.OutpatientInsuranceChargeGrouper.OutpatientInsuranceSettleGroupPlan;
import com.openhis.health.integration.insurance.settlement.support.OutpatientInsuranceContextLoader;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.chs.jilin.contracts.client.ChsOutpatientSettlementClient;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceRegistrationCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceRegistrationResult;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceConfirmCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceConfirmResult;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceCorrectSettleCommand;
import com.openhis.whale.chs.jilin.contracts.command.OpInsurancePreSettleBatchCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsurancePreSettleBatchResult;
import com.openhis.whale.chs.jilin.contracts.command.OpInsurancePreSettleCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsurancePreSettleGroupResult;
import com.openhis.whale.chs.jilin.contracts.result.OpInsurancePreSettleResult;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceRevokeFeeDetailCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceRevokeFeeDetailResult;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceRevokeSettleCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceRevokeSettleResult;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceSettleRecordResult;
import com.openhis.whale.chs.jilin.contracts.dto.OpSelfPayRollupDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 门诊收费 ↔ 吉林医保结算 契约实现（adapter 编排中枢）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>判断费用性质是否医保契约</li>
 *   <li>{@link OutpatientInsuranceContextLoader} 加载 HIS 主数据 → {@link OpSettleContext}</li>
 *   <li>委托 {@link ChsOutpatientSettleOperations} 执行平台 infno</li>
 *   <li>批量预结算时 {@link OutpatientInsuranceChargeGrouper} 分组 + 失败补偿 2205</li>
 * </ul>
 *
 * <p>Extension 层 {@code OutpatientChargeInsuranceExtensionImpl} 委托本 Port，health 不感知 domain DTO。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsOutpatientSettlementClientImpl implements ChsOutpatientSettlementClient {

    private final ContractQueryService contractQueryService;
    private final ChsOutpatientSettleOperations outpatientSettleOperations;
    private final OutpatientInsuranceContextLoader contextLoader;
    private final OutpatientInsuranceChargeGrouper chargeGrouper;
    private final YbClinicPreSettleRepository clinicPreSettleRepository;
    private final YbSettlementRecordRepository settlementRecordRepository;
    private final YbEncounterPlatformMapRepository encounterPlatformMapRepository;
    private final InsuranceContractResolver contractResolver;
    private final CurrentOperatorResolver currentOperatorResolver;

    /**
     * 判断费用性质是否为医保契约（yb_flag 或 category=省医保）。
     *
     * @param contractId {@code fin_contract.id}
     */
    @Override
    public boolean isInsuranceContract(Long contractId) {
        if (contractId == null) {
            return false;
        }
        Contract contract = contractQueryService.findOptionalContractEntityById(contractId).orElse(null);
        if (contract == null) {
            return false;
        }
        // yb_flag 显式标记为医保
        if (Boolean.TRUE.equals(contract.getYbFlag())) {
            return true;
        }
        return InsuranceContractCategorySupport.isInsuranceCategoryEnum(contract.getCategoryEnum());
    }

    /**
     * 【2201】医保门诊平台登记（挂号 Extension 入口）。
     *
     * @param command Port 层登记命令
     * @return mdtrt_id
     */
    @Override
    public OpInsuranceRegistrationResult ensurePlatformRegistration(OpInsuranceRegistrationCommand command) {
        // Pre：HIS 主数据 → OpSettleContext
        OpSettleContext context = contextLoader.loadRegistrationContext(command);
        // Cmd：2201 或复用映射
        OpPlatformRegistrationResult platform = outpatientSettleOperations.ensurePlatformRegistration(context);
        // Asm
        OpInsuranceRegistrationResult result = new OpInsuranceRegistrationResult();
        if (platform != null) {
            result.setMdtrtId(platform.getMdtrtId());
        }
        return result;
    }

    /** 【2202】撤销门诊平台挂号登记。 */
    @Override
    public OpInsuranceRevokeRegistrationResult revokePlatformRegistration(OpInsuranceRevokeRegistrationCommand command) {
        if (command == null || command.getEncounterId() == null || command.getContractId() == null) {
            throw new BusinessException("INS-015", "2202 缺少就诊或费用性质");
        }
        Contract contract = contractResolver.requireContract(null, command.getContractId());
        OpRevokePlatformRegistrationCommand domain = new OpRevokePlatformRegistrationCommand();
        domain.setEncounterId(command.getEncounterId());
        domain.setContractId(command.getContractId());
        domain.setMedType(command.getMedType());
        domain.setContractBusNo(contractResolver.requireContractBusNo(contract));
        domain.setInsuplcAdmdvs(command.getInsuplcAdmdvs());
        domain.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        domain.setTenantId(TenantUtils.getCurrentTenantId());
        OpRevokePlatformRegistrationResult platform =
            outpatientSettleOperations.revokePlatformRegistration(domain);
        OpInsuranceRevokeRegistrationResult result = new OpInsuranceRevokeRegistrationResult();
        if (platform != null) {
            result.setMdtrtId(platform.getMdtrtId());
            result.setRevoked(platform.isRevoked());
        }
        return result;
    }

    /** 批量查询平台挂号登记与预结算摘要。 */
    @Override
    public List<OpInsurancePlatformRegistrationSnapshot> listPlatformRegistrationSnapshots(
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
            encounterPlatformMapRepository.findByEncounterIdInAndTenantIdAndIsDeletedFalse(unique, tenantId);
        Map<Long, YbEncounterPlatformMap> mapByEncounter = new HashMap<>();
        for (YbEncounterPlatformMap row : maps) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            mapByEncounter.putIfAbsent(row.getEncounterId(), row);
        }
        Map<Long, YbClinicPreSettle> preByEncounter = new HashMap<>();
        List<YbClinicPreSettle> preRows = clinicPreSettleRepository
            .findAllByEncounterIdInAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(unique, tenantId);
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
                clinicPreSettleRepository
                    .findFirstByPaymentDraftIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(draftId, tenantId)
                    .ifPresent(pre -> {
                        if (pre.getEncounterId() != null && preSettleStatusRank(pre.getStatus()) > 0) {
                            preByEncounter.put(pre.getEncounterId(), pre);
                        }
                    });
            }
        }
        List<OpInsurancePlatformRegistrationSnapshot> result = new ArrayList<>();
        for (Long encounterId : unique) {
            OpInsurancePlatformRegistrationSnapshot snap = new OpInsurancePlatformRegistrationSnapshot();
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
     * 【2203→2204→2206】单笔门诊预结算。
     *
     * @param command 含 chargeItemIds、读卡字段
     */
    @Override
    public OpInsurancePreSettleResult preSettle(OpInsurancePreSettleCommand command) {
        // Pre
        OpSettleContext context = contextLoader.loadPreSettleContext(command);
        // Cmd
        OpPreSettleResult platform = outpatientSettleOperations.preSettle(context);
        // Asm
        return toPreSettleResult(platform);
    }

    /**
     * 慢病多科室混合预结算：按病种/账户分组，每组独立 chrg_bchno 调用 preSettle。
     *
     * <p>任一组失败时 {@link #cancelCompletedGroups} 对已完成的组调 2205 补偿撤销。</p>
     *
     * @param command 全部 chargeItemIds
     * @return groups + selfPayRollup + totalPsnCashPay
     */
    @Override
    public OpInsurancePreSettleBatchResult preSettleBatch(OpInsurancePreSettleBatchCommand command) {
        // Pre：按 dise_codg/med_type/account 拆分医保组与自费组
        OutpatientInsuranceChargeSplitResult split = chargeGrouper.split(
            command.getEncounterId(), command.getChargeItemIds());

        List<OpInsurancePreSettleGroupResult> completedGroups = new ArrayList<>();
        try {
            // Cmd：逐组 preSettle（每组独立 2203→2204→2206）
            for (OutpatientInsuranceSettleGroupPlan plan : split.getInsuranceGroups()) {
                OpInsurancePreSettleCommand groupCommand = this.toGroupCommand(command, plan);
                OpSettleContext context = contextLoader.loadPreSettleContextForGroup(
                    groupCommand,
                    plan.getChargeItems(),
                    plan.getMedType(),
                    plan.getDiseCodg(),
                    plan.getDiseName());
                context.setAccountId(plan.getAccountId());
                OpPreSettleResult platform = outpatientSettleOperations.preSettle(context);
                OpInsurancePreSettleGroupResult groupResult = toGroupResult(platform, plan);
                completedGroups.add(groupResult);
            }
        } catch (RuntimeException ex) {
            // 补偿：撤销已成功组的 2204/2206（2205）
            this.cancelCompletedGroups(completedGroups);
            throw ex;
        }

        // Asm：汇总自费 rollup 与各组 psn_cash_pay
        OpSelfPayRollupDto selfPayRollup = this.buildSelfPayRollup(split);
        BigDecimal totalPsnCashPay = BigDecimal.ZERO;
        for (OpInsurancePreSettleGroupResult group : completedGroups) {
            if (group.getPsnCashPay() != null) {
                totalPsnCashPay = totalPsnCashPay.add(group.getPsnCashPay());
            }
        }
        if (selfPayRollup != null && selfPayRollup.getTotalAmount() != null) {
            totalPsnCashPay = totalPsnCashPay.add(selfPayRollup.getTotalAmount());
        }

        OpInsurancePreSettleBatchResult result = new OpInsurancePreSettleBatchResult();
        result.setGroups(completedGroups);
        result.setSelfPayRollup(selfPayRollup);
        result.setTotalPsnCashPay(totalPsnCashPay);
        return result;
    }

    /**
     * 【2207】本地收银前正式结算。
     *
     * @param command 含 paymentDraftId、chrg_bchno
     */
    @Override
    public OpInsuranceConfirmResult confirmBeforeLocalSettle(OpInsuranceConfirmCommand command) {
        // Pre：Port → domain Command，注入操作员与租户
        OpConfirmSettleCommand domainCommand = new OpConfirmSettleCommand();
        domainCommand.setPaymentDraftId(command.getPaymentDraftId());
        domainCommand.setChrgBchno(command.getChrgBchno());
        domainCommand.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        domainCommand.setTenantId(TenantUtils.getCurrentTenantId());
        // Cmd
        OpConfirmSettleResult platform = outpatientSettleOperations.confirmSettle(domainCommand);
        // Asm
        OpInsuranceConfirmResult result = new OpInsuranceConfirmResult();
        result.setSetlId(platform.getSetlId());
        result.setChrgBchno(platform.getChrgBchno());
        result.setMdtrtId(platform.getMdtrtId());
        result.setSettlementRecordId(platform.getSettlementRecordId());
        result.setAccountId(platform.getAccountId());
        result.setMedfeeSumamt(platform.getMedfeeSumamt());
        result.setFundPaySumamt(platform.getFundPaySumamt());
        result.setAcctPay(platform.getAcctPay());
        result.setPsnCashPay(platform.getPsnCashPay());
        return result;
    }

    /** 查草稿下 ACTIVE 预结算列表。 */
    @Override
    public List<OpInsurancePreSettleResult> listActivePreSettlesByPaymentDraft(Long paymentDraftId) {
        if (paymentDraftId == null) {
            return List.of();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<YbClinicPreSettle> rows = clinicPreSettleRepository
            .findAllByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
                paymentDraftId, YbClinicPreSettle.STATUS_ACTIVE, tenantId);
        List<OpInsurancePreSettleResult> results = new ArrayList<>();
        for (YbClinicPreSettle row : rows) {
            OpInsurancePreSettleResult item = new OpInsurancePreSettleResult();
            item.setPreSettleId(row.getId());
            item.setChrgBchno(row.getChrgBchno());
            item.setMdtrtId(row.getMdtrtId());
            item.setMedfeeSumamt(row.getMedfeeSumamt());
            item.setFundPaySumamt(row.getFundPaySumamt());
            item.setAcctPay(row.getAcctPay());
            item.setPsnCashPay(row.getPsnCashPay());
            item.setPsnNo(row.getPsnNo());
            item.setInsuplcAdmdvs(row.getInsuplcAdmdvs());
            item.setInsutype(row.getInsutype());
            item.setMdtrtCertType(row.getMdtrtCertType());
            item.setAcctUsedFlag(row.getAcctUsedFlag());
            results.add(item);
        }
        return results;
    }

    /** 按 payment 查 yb_settlement_record 回执列表。 */
    @Override
    public List<OpInsuranceSettleRecordResult> listSettlementsByPayment(Long paymentId) {
        if (paymentId == null) {
            return List.of();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<YbSettlementRecord> rows = settlementRecordRepository
            .findAllByPaymentIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(paymentId, tenantId);
        List<OpInsuranceSettleRecordResult> results = new ArrayList<>();
        for (YbSettlementRecord row : rows) {
            OpInsuranceSettleRecordResult item = new OpInsuranceSettleRecordResult();
            item.setSettlementRecordId(row.getId());
            item.setPaymentId(row.getPaymentId());
            item.setPreSettleId(row.getPreSettleId());
            item.setContractId(row.getContractId());
            item.setChrgBchno(row.getChrgBchno());
            item.setSetlId(row.getSetlId());
            item.setMdtrtId(row.getMdtrtId());
            item.setMsgid2207(row.getMsgid2207());
            results.add(item);
        }
        return results;
    }

    /** 【2208】撤销正式结算。 */
    @Override
    public OpInsuranceRevokeSettleResult revokeSettle(OpInsuranceRevokeSettleCommand command) {
        OpRevokeSettleCommand domainCommand = new OpRevokeSettleCommand();
        domainCommand.setSettlementRecordId(command.getSettlementRecordId());
        domainCommand.setPaymentId(command.getPaymentId());
        domainCommand.setPreSettleId(command.getPreSettleId());
        domainCommand.setSetlId(command.getSetlId());
        domainCommand.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        domainCommand.setTenantId(TenantUtils.getCurrentTenantId());
        OpRevokeSettleResult platform = outpatientSettleOperations.revokeSettle(domainCommand);
        OpInsuranceRevokeSettleResult result = new OpInsuranceRevokeSettleResult();
        result.setSettlementRecordId(platform.getSettlementRecordId());
        result.setSetlId(platform.getSetlId());
        result.setMdtrtId(platform.getMdtrtId());
        return result;
    }

    /**
     * 【2601】冲正原 2207 交易（补偿入口，从回执反查 omsgid）。
     *
     * @param command 含 settlementRecordId；originalInfno 缺省 2207
     */
    @Override
    public void correctSettleTransaction(OpInsuranceCorrectSettleCommand command) {
        // Gate
        if (command == null || command.getSettlementRecordId() == null) {
            throw new BusinessException("INS-014", "2601 冲正缺少 settlementRecordId");
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        // Pre：加载结算回执
        YbSettlementRecord record = settlementRecordRepository.findById(command.getSettlementRecordId())
            .filter(row -> tenantId.equals(row.getTenantId()) && !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(() -> new BusinessException("INS-014", "结算回执不存在"));
        String omsgid = null;
        if (StringUtils.isNotBlank(record.getMsgid2207())) {
            omsgid = record.getMsgid2207().trim();
        } else {
            omsgid = StringUtils.trimToNull(record.getMedinsSetlId());
        }
        if (StringUtils.isBlank(omsgid)) {
            throw new BusinessException("INS-014", "结算回执缺少 msgid，无法冲正");
        }
        String oinfno = com.openhis.chs.api.ChsInfNo.OUTPATIENT_SETTLE;
        if (StringUtils.isNotBlank(command.getOriginalInfno())) {
            oinfno = command.getOriginalInfno().trim();
        }
        // Cmd
        SettlementCorrectionCommand domainCommand = new SettlementCorrectionCommand()
            .setSettlementRecordId(record.getId())
            .setPsnNo(record.getPsnNo())
            .setOmsgid(omsgid)
            .setOinfno(oinfno)
            .setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"))
            .setTenantId(tenantId);
        outpatientSettleOperations.correctTransaction(domainCommand);
    }

    /** 【2205】撤销未正式结算的费用明细。 */
    @Override
    public OpInsuranceRevokeFeeDetailResult revokeFeeDetails(OpInsuranceRevokeFeeDetailCommand command) {
        OpRevokeFeeDetailCommand domainCommand = this.toRevokeFeeDetailCommand(command);
        OpRevokeFeeDetailResult platform = outpatientSettleOperations.revokeFeeDetails(domainCommand);
        OpInsuranceRevokeFeeDetailResult result = new OpInsuranceRevokeFeeDetailResult();
        result.setChrgBchno(platform.getChrgBchno());
        result.setRevoked(platform.isRevoked());
        return result;
    }

    /**
     * Port 2205 命令 → domain 命令；chrg_bchno 缺失时从 paymentDraftId 反查 ACTIVE 预结算快照。
     *
     * <p>补偿场景（{@link #cancelCompletedGroups}）直接传 chrgBchno；
     * 收银撤销场景可能只传 paymentDraftId，取最后一条 ACTIVE 快照的批次号。</p>
     */
    private OpRevokeFeeDetailCommand toRevokeFeeDetailCommand(OpInsuranceRevokeFeeDetailCommand command) {
        OpRevokeFeeDetailCommand domain = new OpRevokeFeeDetailCommand();
        domain.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        domain.setTenantId(TenantUtils.getCurrentTenantId());
        if (command == null) {
            return domain;
        }
        // 优先使用调用方显式传入的 chrg_bchno
        if (StringUtils.isNotBlank(command.getChrgBchno())) {
            domain.setChrgBchno(command.getChrgBchno().trim());
            return domain;
        }
        // 兜底：按 paymentDraftId 查 ACTIVE 预结算，取最新一条的 chrg_bchno
        if (command.getPaymentDraftId() != null) {
            Long tenantId = domain.getTenantId();
            List<YbClinicPreSettle> active = clinicPreSettleRepository
                .findAllByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
                    command.getPaymentDraftId(), YbClinicPreSettle.STATUS_ACTIVE, tenantId);
            if (!active.isEmpty() && StringUtils.isNotBlank(active.get(active.size() - 1).getChrgBchno())) {
                domain.setChrgBchno(active.get(active.size() - 1).getChrgBchno().trim());
            }
        }
        return domain;
    }

    /** 混合预结算单组：batch 公共字段 + plan 组内 medType/chargeItemIds。 */
    private OpInsurancePreSettleCommand toGroupCommand(
        OpInsurancePreSettleBatchCommand batch,
        OutpatientInsuranceSettleGroupPlan plan) {
        List<Long> groupIds = new ArrayList<>();
        for (ChargeItem item : plan.getChargeItems()) {
            groupIds.add(item.getId());
        }
        OpInsurancePreSettleCommand command = new OpInsurancePreSettleCommand();
        command.setEncounterId(batch.getEncounterId());
        command.setChargeItemIds(groupIds);
        command.setContractId(batch.getContractId());
        command.setPaymentDraftId(batch.getPaymentDraftId());
        command.setMdtrtCertType(batch.getMdtrtCertType());
        command.setMdtrtCertNo(batch.getMdtrtCertNo());
        command.setPsnNo(batch.getPsnNo());
        command.setInsuplcAdmdvs(batch.getInsuplcAdmdvs());
        command.setInsutype(batch.getInsutype());
        command.setMedType(plan.getMedType());
        return command;
    }

    /** 汇总纯自费 chargeItem 原价，供收银展示「患者应付 = 医保现金 + 自费」。 */
    private OpSelfPayRollupDto buildSelfPayRollup(OutpatientInsuranceChargeSplitResult split) {
        if (split.getSelfPayItems() == null || split.getSelfPayItems().isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        List<Long> ids = new ArrayList<>();
        for (ChargeItem item : split.getSelfPayItems()) {
            ids.add(item.getId());
            if (item.getTotalPrice() != null) {
                total = total.add(item.getTotalPrice());
            }
        }
        OpSelfPayRollupDto rollup = new OpSelfPayRollupDto();
        rollup.setAccountId(split.getSelfPayAccountId());
        rollup.setChargeItemIds(ids);
        rollup.setTotalAmount(total);
        return rollup;
    }

    /**
     * 批量预结算失败补偿：对已成功的组逐笔调 2205 撤销平台费用明细。
     *
     * <p>混合结算任一组 2204/2206 失败时，须回滚已成功的组，避免平台残留 ACTIVE 批次。</p>
     */
    private void cancelCompletedGroups(List<OpInsurancePreSettleGroupResult> completedGroups) {
        for (OpInsurancePreSettleGroupResult group : completedGroups) {
            if (group == null || StringUtils.isBlank(group.getChrgBchno())) {
                continue;
            }
            OpInsuranceRevokeFeeDetailCommand cmd = new OpInsuranceRevokeFeeDetailCommand();
            cmd.setChrgBchno(group.getChrgBchno().trim());
            this.revokeFeeDetails(cmd);
        }
    }

    /** domain 2206 结果 → Port 单组预结算 DTO。 */
    private OpInsurancePreSettleResult toPreSettleResult(OpPreSettleResult platform) {
        OpInsurancePreSettleResult result = new OpInsurancePreSettleResult();
        result.setPreSettleId(platform.getPreSettleId());
        result.setChrgBchno(platform.getChrgBchno());
        result.setMdtrtId(platform.getMdtrtId());
        result.setMedfeeSumamt(platform.getMedfeeSumamt());
        result.setFundPaySumamt(platform.getFundPaySumamt());
        result.setAcctPay(platform.getAcctPay());
        result.setPsnCashPay(platform.getPsnCashPay());
        return result;
    }

    /** domain 2206 结果 + 分组计划 → Port 混合预结算组 DTO（含 groupIndex/diseCodg/accountId）。 */
    private OpInsurancePreSettleGroupResult toGroupResult(
        OpPreSettleResult platform,
        OutpatientInsuranceSettleGroupPlan plan) {
        OpInsurancePreSettleGroupResult result = new OpInsurancePreSettleGroupResult();
        result.setPreSettleId(platform.getPreSettleId());
        result.setChrgBchno(platform.getChrgBchno());
        result.setMdtrtId(platform.getMdtrtId());
        result.setMedfeeSumamt(platform.getMedfeeSumamt());
        result.setFundPaySumamt(platform.getFundPaySumamt());
        result.setAcctPay(platform.getAcctPay());
        result.setPsnCashPay(platform.getPsnCashPay());
        result.setGroupIndex(plan.getGroupIndex());
        result.setMedType(plan.getMedType());
        result.setDiseCodg(plan.getDiseCodg());
        result.setAccountId(plan.getAccountId());
        List<Long> chargeItemIds = new ArrayList<>();
        for (ChargeItem item : plan.getChargeItems()) {
            chargeItemIds.add(item.getId());
        }
        result.setChargeItemIds(chargeItemIds);
        return result;
    }

    /** ACTIVE 优先于 SETTLED；CANCELLED/REVOKED 不参与列表 enrichment。 */
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
