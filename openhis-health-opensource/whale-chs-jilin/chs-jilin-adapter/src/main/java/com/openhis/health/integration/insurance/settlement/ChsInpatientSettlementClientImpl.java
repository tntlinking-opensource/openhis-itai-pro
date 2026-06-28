/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.settlement;

import com.openhis.chs.domain.inpatient.dto.IpConfirmSettleCommand;
import com.openhis.chs.domain.inpatient.dto.IpAdmissionResult;
import com.openhis.chs.domain.inpatient.dto.IpConfirmSettleResult;
import com.openhis.chs.domain.inpatient.dto.IpEncounterPlatformCommand;
import com.openhis.chs.domain.inpatient.dto.IpPreSettleResult;
import com.openhis.chs.domain.inpatient.dto.IpRevokeFeeDetailCommand;
import com.openhis.chs.domain.inpatient.dto.IpRevokeFeeDetailResult;
import com.openhis.chs.domain.inpatient.dto.IpRevokeSettleCommand;
import com.openhis.chs.domain.inpatient.dto.IpRevokeSettleResult;
import com.openhis.chs.domain.inpatient.dto.IpSettleContext;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.inpatient.facade.ChsInpatientSettleOperations;
import com.openhis.chs.domain.inpatient.repository.YbInpatientPreSettleRepository;
import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.repository.YbEncounterPlatformMapRepository;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.utils.InsuranceContractCategorySupport;
import com.openhis.health.integration.insurance.settlement.support.InpatientInsuranceContextLoader;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.chs.jilin.contracts.client.ChsInpatientSettlementClient;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceAdmissionCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsuranceAdmissionResult;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceConfirmCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsuranceConfirmResult;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceEncounterCommand;
import com.openhis.whale.chs.jilin.contracts.command.IpInsurancePreSettleCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsurancePlatformAdmissionSnapshot;
import com.openhis.whale.chs.jilin.contracts.result.IpInsurancePreSettleResult;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceRevokeFeeDetailCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsuranceRevokeFeeDetailResult;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceRevokeSettleCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsuranceRevokeSettleResult;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 住院收银 ↔ 吉林医保结算 契约实现（adapter 编排中枢，镜像 {@link ChsOutpatientSettlementClientImpl}）。
 *
 * <p>平台链路：</p>
 * <ul>
 *   <li>入院：2401 → {@link #ensureAdmission}</li>
 *   <li>预结算：2301→(2402)→2303 → {@link #preSettle}</li>
 *   <li>正式结算：2304 → {@link #confirmBeforeLocalSettle}</li>
 *   <li>撤销：2305/2302/2404/2405/2403</li>
 * </ul>
 *
 * <p>HIS 上下文由 {@link InpatientInsuranceContextLoader} 组装；平台编排委托 {@link ChsInpatientSettleOperations}。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsInpatientSettlementClientImpl implements ChsInpatientSettlementClient {

    private final ContractQueryService contractQueryService;
    private final ChsInpatientSettleOperations inpatientSettleOperations;
    private final InpatientInsuranceContextLoader contextLoader;
    private final YbInpatientPreSettleRepository inpatientPreSettleRepository;
    private final YbEncounterPlatformMapRepository encounterPlatformMapRepository;
    private final CurrentOperatorResolver currentOperatorResolver;

    /**
     * 判断费用性质是否为医保契约。
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
        if (Boolean.TRUE.equals(contract.getYbFlag())) {
            return true;
        }
        return InsuranceContractCategorySupport.isInsuranceCategoryEnum(contract.getCategoryEnum());
    }

    /**
     * 【2401】医保入院登记，获取 mdtrt_id。
     *
     * @param command 入院读卡与契约信息
     */
    @Override
    public IpInsuranceAdmissionResult ensureAdmission(IpInsuranceAdmissionCommand command) {
        // Pre：HIS → IpSettleContext
        IpSettleContext context = contextLoader.loadAdmissionContext(command);
        // Cmd
        IpAdmissionResult platform = inpatientSettleOperations.ensureAdmission(context);
        // Asm
        IpInsuranceAdmissionResult result = new IpInsuranceAdmissionResult();
        result.setMdtrtId(platform.getMdtrtId());
        return result;
    }

    /**
     * 【2301→(2402)→2303】住院预结算。
     *
     * @param command 含 settleMode（出院全结/中途结算）、chargeItemIds
     */
    @Override
    public IpInsurancePreSettleResult preSettle(IpInsurancePreSettleCommand command) {
        // Pre
        IpSettleContext context = contextLoader.loadPreSettleContext(command);
        // Cmd
        IpPreSettleResult platform = inpatientSettleOperations.preSettle(context);
        // Asm
        return toPreSettleResult(platform);
    }

    /**
     * 【2304】本地落账前正式结算。
     *
     * @param command 含 preSettleId 或 paymentDraftId
     */
    @Override
    public IpInsuranceConfirmResult confirmBeforeLocalSettle(IpInsuranceConfirmCommand command) {
        // Pre：注入操作员与租户
        IpConfirmSettleCommand domainCommand = new IpConfirmSettleCommand();
        domainCommand.setPaymentDraftId(command.getPaymentDraftId());
        domainCommand.setPreSettleId(command.getPreSettleId());
        domainCommand.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        domainCommand.setTenantId(TenantUtils.getCurrentTenantId());
        // Cmd
        IpConfirmSettleResult platform = inpatientSettleOperations.confirmSettle(domainCommand);
        // Asm
        IpInsuranceConfirmResult result = new IpInsuranceConfirmResult();
        result.setSetlId(platform.getSetlId());
        result.setChrgBchno(platform.getChrgBchno());
        result.setMdtrtId(platform.getMdtrtId());
        result.setSettlementRecordId(platform.getSettlementRecordId());
        result.setMedfeeSumamt(platform.getMedfeeSumamt());
        result.setFundPaySumamt(platform.getFundPaySumamt());
        result.setAcctPay(platform.getAcctPay());
        result.setPsnCashPay(platform.getPsnCashPay());
        return result;
    }

    /** 查草稿下 ACTIVE 预结算列表。 */
    @Override
    public List<IpInsurancePreSettleResult> listActivePreSettlesByPaymentDraft(Long paymentDraftId) {
        if (paymentDraftId == null) {
            return List.of();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<YbInpatientPreSettle> rows = inpatientPreSettleRepository
            .findAllByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
                paymentDraftId, YbInpatientPreSettle.STATUS_ACTIVE, tenantId);
        List<IpInsurancePreSettleResult> results = new ArrayList<>();
        for (YbInpatientPreSettle row : rows) {
            results.add(toPreSettleResult(row));
        }
        return results;
    }

    /**
     * 按 payment 查最近一条 SETTLED 预结算（退费/取消结算定位用）。
     *
     * @param paymentId {@code fin_payment.id}（此处与 draft id 同列存储）
     */
    @Override
    public IpInsurancePreSettleResult findSettledPreSettleByPayment(Long paymentId) {
        if (paymentId == null) {
            return null;
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        Optional<YbInpatientPreSettle> row = inpatientPreSettleRepository
            .findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                paymentId, YbInpatientPreSettle.STATUS_SETTLED, tenantId);
        if (row.isEmpty()) {
            return null;
        }
        return toPreSettleResult(row.get());
    }

    /** 【2305】撤销住院正式结算。 */
    @Override
    public IpInsuranceRevokeSettleResult revokeSettle(IpInsuranceRevokeSettleCommand command) {
        // Pre
        IpRevokeSettleCommand domainCommand = new IpRevokeSettleCommand();
        domainCommand.setSettlementRecordId(command.getSettlementRecordId());
        domainCommand.setPaymentId(command.getPaymentId());
        domainCommand.setPreSettleId(command.getPreSettleId());
        domainCommand.setSetlId(command.getSetlId());
        domainCommand.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        domainCommand.setTenantId(TenantUtils.getCurrentTenantId());
        // Cmd
        IpRevokeSettleResult platform = inpatientSettleOperations.revokeSettle(domainCommand);
        // Asm
        IpInsuranceRevokeSettleResult result = new IpInsuranceRevokeSettleResult();
        result.setSettlementRecordId(platform.getSettlementRecordId());
        result.setSetlId(platform.getSetlId());
        result.setMdtrtId(platform.getMdtrtId());
        return result;
    }

    /** 【2302】撤销已上传未结算的住院费用明细。 */
    @Override
    public IpInsuranceRevokeFeeDetailResult revokeFeeDetails(IpInsuranceRevokeFeeDetailCommand command) {
        // Pre：从 yb_encounter_platform_map 反查 mdtrt_id/psn_no
        IpRevokeFeeDetailCommand domainCommand = contextLoader.loadRevokeFeeDetailCommand(command);
        // Cmd
        IpRevokeFeeDetailResult platform = inpatientSettleOperations.revokeFeeDetails(domainCommand);
        // Asm
        IpInsuranceRevokeFeeDetailResult result = new IpInsuranceRevokeFeeDetailResult();
        result.setRevokedCount(platform.getRevokedCount());
        return result;
    }

    /** 批量查询平台入院登记快照（yb_encounter_platform_map）。 */
    @Override
    public List<IpInsurancePlatformAdmissionSnapshot> listPlatformAdmissionSnapshots(List<Long> encounterIds) {
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
        List<IpInsurancePlatformAdmissionSnapshot> result = new ArrayList<>();
        for (Long encounterId : unique) {
            IpInsurancePlatformAdmissionSnapshot snap = new IpInsurancePlatformAdmissionSnapshot();
            snap.setEncounterId(encounterId);
            YbEncounterPlatformMap mapped = mapByEncounter.get(encounterId);
            if (mapped != null) {
                snap.setContractId(mapped.getContractId());
                snap.setMdtrtId(mapped.getMdtrtId());
                snap.setPlatformAdmitted(Boolean.TRUE);
            } else {
                snap.setPlatformAdmitted(Boolean.FALSE);
            }
            result.add(snap);
        }
        return result;
    }

    /** 【2404】撤销入院登记并软删本地 mdtrt_id 映射。 */
    @Override
    public void revokeAdmission(IpInsuranceEncounterCommand command) {
        IpEncounterPlatformCommand domainCommand = contextLoader.loadEncounterPlatformCommand(command);
        inpatientSettleOperations.revokeAdmission(domainCommand);
    }

    /** 【2405】撤销出院登记。 */
    @Override
    public void revokeDischarge(IpInsuranceEncounterCommand command) {
        IpEncounterPlatformCommand domainCommand = contextLoader.loadEncounterPlatformCommand(command);
        inpatientSettleOperations.revokeDischarge(domainCommand);
    }

    /** 【2403】变更住院医保登记信息。 */
    @Override
    public void changeInpatientInfo(IpInsuranceAdmissionCommand command) {
        IpSettleContext context = contextLoader.loadAdmissionContext(command);
        inpatientSettleOperations.changeInpatientInfo(context);
    }

    private IpInsurancePreSettleResult toPreSettleResult(IpPreSettleResult platform) {
        IpInsurancePreSettleResult result = new IpInsurancePreSettleResult();
        result.setPreSettleId(platform.getPreSettleId());
        result.setMdtrtId(platform.getMdtrtId());
        result.setSettleMode(platform.getSettleMode());
        result.setMedfeeSumamt(platform.getMedfeeSumamt());
        result.setFundPaySumamt(platform.getFundPaySumamt());
        result.setAcctPay(platform.getAcctPay());
        result.setPsnCashPay(platform.getPsnCashPay());
        return result;
    }

    private IpInsurancePreSettleResult toPreSettleResult(YbInpatientPreSettle row) {
        IpInsurancePreSettleResult result = new IpInsurancePreSettleResult();
        result.setPreSettleId(row.getId());
        result.setContractId(row.getContractId());
        result.setMdtrtId(row.getMdtrtId());
        result.setSetlId(row.getSetlId());
        result.setSettleMode(row.getSettleMode());
        result.setMedfeeSumamt(row.getMedfeeSumamt());
        result.setFundPaySumamt(row.getFundPaySumamt());
        result.setAcctPay(row.getAcctPay());
        result.setPsnCashPay(row.getPsnCashPay());
        return result;
    }
}
