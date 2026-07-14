/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.settlement.constants.ChsSettleEncounterClass;
import com.openhis.chs.domain.settlement.YbPreSettleStatus;
import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.repository.YbSettlementRecordRepository;
import com.openhis.wi.domain.share.WorkInjuryConstants;
import com.openhis.wi.domain.share.WiErrorCodes;
import com.openhis.wi.domain.entity.YbWiPreSettle;
import com.openhis.wi.domain.repository.YbWiPreSettleRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 2207/2208 正式结算回执 {@code yb_settlement_record}（settle_mode=WORK_INJURY）。 */
@Service
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
@RequiredArgsConstructor
public class YbWiSettlementDomainService {

    private final YbWiPreSettleRepository preSettleRepository;
    private final YbSettlementRecordRepository settlementRecordRepository;

    /**
     * 【2207】正式结算写入 {@code yb_settlement_record}（settle_mode=WORK_INJURY）。
     *
     * @param encounterId       就诊 ID
     * @param contractId        费用性质 contract（可空，回退预结算行）
     * @param paymentDraftId    收银草稿 ID
     * @param setlId            2207 出参 setl_id
     * @param mdtrtId           2207 出参 mdtrt_id
     * @param psnNo             人员编号（可空，回退预结算行）
     * @param chrgBchno         收费批次（可空，回退预结算行）
     * @param medfeeSumamt      医疗费总额
     * @param fundPaySumamt     统筹支付
     * @param psnCashPay        个人现金
     * @param fundBreakdownJson 2207 出参 JSON
     * @param winMsgid          Win msgid（写 msgid_2207）
     * @param tenantId          租户 ID
     * @return 持久化后的结算回执；同 setl_id 幂等返回已有行
     */
    @Transactional
    public YbSettlementRecord saveConfirmedFrom2207(
            Long encounterId,
            Long contractId,
            Long paymentDraftId,
            String setlId,
            String mdtrtId,
            String psnNo,
            String chrgBchno,
            BigDecimal medfeeSumamt,
            BigDecimal fundPaySumamt,
            BigDecimal psnCashPay,
            String fundBreakdownJson,
            String winMsgid,
            Long tenantId) {
        if (isEmpty(setlId)) {
            throw new BusinessException(WiErrorCodes.PLATFORM_INFCODE_FAILED, "2207 出参缺少 setl_id");
        }
        Long resolvedTenantId = resolveTenantId(tenantId);
        String normalizedSetlId = setlId.trim();
        Optional<YbSettlementRecord> existing = settlementRecordRepository
                .findFirstBySetlIdAndTenantIdAndIsDeletedFalse(normalizedSetlId, resolvedTenantId);
        if (existing.isPresent()) {
            return existing.get();
        }

        YbWiPreSettle pre = resolvePreSettle(encounterId, paymentDraftId, resolvedTenantId);
        Long resolvedContractId = pre.getContractId();
        if (contractId != null) {
            resolvedContractId = contractId;
        }
        Long resolvedEncounterId = pre.getEncounterId();
        if (encounterId != null) {
            resolvedEncounterId = encounterId;
        }
        YbSettlementRecord row = new YbSettlementRecord();
        row.setContractId(resolvedContractId);
        row.setEncounterId(resolvedEncounterId);
        row.setPaymentId(paymentDraftId);
        row.setPreSettleId(pre.getId());
        row.setEncounterClass(ChsSettleEncounterClass.AMB);
        row.setStatus(YbSettlementVoucherStatus.CONFIRMED);
        row.setSettleMode(WorkInjuryConstants.SCHEME_CODE);
        row.setChrgBchno(firstNonBlank(chrgBchno, pre.getChrgBchno()));
        row.setSetlId(normalizedSetlId);
        row.setMdtrtId(firstNonBlank(mdtrtId, pre.getMdtrtId()));
        row.setPsnNo(firstNonBlank(psnNo, pre.getPsnNo()));
        row.setMedfeeSumamt(firstNonNull(medfeeSumamt, pre.getMedfeeSumamt()));
        row.setFundPaySumamt(firstNonNull(fundPaySumamt, pre.getFundPaySumamt()));
        row.setHifpPay(fundPaySumamt);
        row.setAcctPay(BigDecimal.ZERO);
        row.setPsnCashPay(firstNonNull(psnCashPay, pre.getPsnCashPay()));
        if (isNotEmpty(winMsgid)) {
            row.setMsgid2207(winMsgid.trim());
        }
        row.setFundBreakdownJson(fundBreakdownJson);
        row.setTenantId(resolvedTenantId);
        row.setCreatedAt(LocalDateTime.now());
        return settlementRecordRepository.saveAndFlush(row);
    }

    /**
     * 【2208】结算回执标记 REVOKED。
     *
     * @param settlementRecordId yb_settlement_record.id
     * @param tenantId           租户 ID
     * @return 更新后的回执；已 REVOKED 时幂等返回
     */
    @Transactional
    public YbSettlementRecord markRevoked2208(Long settlementRecordId, Long tenantId) {
        if (settlementRecordId == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "settlementRecordId 不能为空");
        }
        Long resolvedTenantId = resolveTenantId(tenantId);
        YbSettlementRecord row = settlementRecordRepository
                .findFirstByIdAndTenantIdAndIsDeletedFalse(settlementRecordId, resolvedTenantId)
                .orElseThrow(() -> new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "结算回执不存在"));
        if (YbSettlementVoucherStatus.REVOKED.equals(row.getStatus())) {
            return row;
        }
        row.setStatus(YbSettlementVoucherStatus.REVOKED);
        return settlementRecordRepository.save(row);
    }

    /**
     * 2208 前校验：工伤模式、未撤销。
     *
     * @param settlementRecordId 结算回执 ID
     * @param tenantId           租户 ID
     * @return 可撤销的回执
     */
    public YbSettlementRecord requireRevocableRecord(Long settlementRecordId, Long tenantId) {
        if (settlementRecordId == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "settlementRecordId 不能为空");
        }
        Long resolvedTenantId = resolveTenantId(tenantId);
        YbSettlementRecord row = settlementRecordRepository
                .findFirstByIdAndTenantIdAndIsDeletedFalse(settlementRecordId, resolvedTenantId)
                .orElseThrow(() -> new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "结算回执不存在"));
        if (!WorkInjuryConstants.SCHEME_CODE.equals(row.getSettleMode())) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "非工伤结算回执");
        }
        if (YbSettlementVoucherStatus.REVOKED.equals(row.getStatus())) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "结算回执已撤销");
        }
        return row;
    }

    /**
     * 按 paymentId 列工伤结算回执（退费页）。
     *
     * @param paymentId 收银 payment.id
     * @param tenantId  租户 ID
     * @return settle_mode=WORK_INJURY 的回执列表
     */
    public List<YbSettlementRecord> listWorkInjuryByPayment(Long paymentId, Long tenantId) {
        if (paymentId == null) {
            return List.of();
        }
        return settlementRecordRepository.findAllByPaymentIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
                        paymentId, resolveTenantId(tenantId))
                .stream()
                .filter(row -> WorkInjuryConstants.SCHEME_CODE.equals(row.getSettleMode()))
                .toList();
    }

    /**
     * 收银完成后回写 paymentId 到结算回执。
     *
     * @param settlementRecordId 结算回执 ID
     * @param paymentId          正式 payment.id
     * @param tenantId           租户 ID
     */
    @Transactional
    public void bindPaymentId(Long settlementRecordId, Long paymentId, Long tenantId) {
        if (settlementRecordId == null || paymentId == null) {
            return;
        }
        YbSettlementRecord row = settlementRecordRepository
                .findFirstByIdAndTenantIdAndIsDeletedFalse(settlementRecordId, resolveTenantId(tenantId))
                .orElseThrow(() -> new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "结算回执不存在"));
        row.setPaymentId(paymentId);
        settlementRecordRepository.save(row);
    }

    /**
     * 按 paymentDraft + chrg_bchno 定位 2207 回执（confirmSettle 用）。
     *
     * @param paymentDraftId 收银草稿 ID
     * @param chrgBchno      收费批次（可空）
     * @param tenantId       租户 ID
     * @return 2207 结算回执
     */
    public Optional<YbSettlementRecord> findByPaymentDraft(Long paymentDraftId, String chrgBchno, Long tenantId) {
        if (paymentDraftId == null) {
            return Optional.empty();
        }
        Long resolvedTenantId = resolveTenantId(tenantId);
        Optional<YbWiPreSettle> preOpt = preSettleRepository
                .findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                        paymentDraftId, YbPreSettleStatus.SETTLED.name(), resolvedTenantId);
        if (preOpt.isEmpty()) {
            return Optional.empty();
        }
        YbWiPreSettle pre = preOpt.get();
        if (isNotEmpty(chrgBchno) && !chrgBchno.trim().equals(pre.getChrgBchno())) {
            return Optional.empty();
        }
        return settlementRecordRepository.findFirstByPreSettleIdAndTenantIdAndIsDeletedFalse(
                pre.getId(), resolvedTenantId);
    }

    /**
     * 按主键查结算回执（2208 invoke 上下文）。
     *
     * @param settlementRecordId 结算回执 ID
     * @param tenantId           租户 ID
     * @return 结算回执
     */
    public Optional<YbSettlementRecord> findByIdAndTenant(Long settlementRecordId, Long tenantId) {
        if (settlementRecordId == null) {
            return Optional.empty();
        }
        return settlementRecordRepository.findFirstByIdAndTenantIdAndIsDeletedFalse(
                settlementRecordId, resolveTenantId(tenantId));
    }

    /**
     * 2207 落账前定位 ACTIVE 预结算行（paymentDraft 优先，回退 encounter）。
     *
     * @param encounterId    就诊 id
     * @param paymentDraftId 收银草稿 id
     * @param tenantId       租户 id
     * @return ACTIVE 预结算行
     */
    private YbWiPreSettle resolvePreSettle(Long encounterId, Long paymentDraftId, Long tenantId) {
        Optional<YbWiPreSettle> pre;
        if (paymentDraftId != null) {
            pre = preSettleRepository
                    .findFirstByEncounterIdAndPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                            encounterId, paymentDraftId, YbPreSettleStatus.ACTIVE.name(), tenantId);
            if (pre.isEmpty()) {
                pre = preSettleRepository
                        .findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                                paymentDraftId, YbPreSettleStatus.ACTIVE.name(), tenantId);
            }
        } else if (encounterId != null) {
            pre = preSettleRepository
                    .findFirstByEncounterIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                            encounterId, YbPreSettleStatus.ACTIVE.name(), tenantId);
        } else {
            pre = Optional.empty();
        }
        return pre.orElseThrow(() -> new BusinessException(
                WiErrorCodes.INVALID_INVOKE_SESSION, "未找到 2206 预结算快照，请先完成预结算"));
    }

    /** 租户 id 回退：参数空则取当前租户。 */
    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }

    /** 字符串择优：primary 非空 trim，否则 fallback trim，均空返回 ""。 */
    private String firstNonBlank(String primary, String fallback) {
        if (isNotEmpty(primary)) {
            return primary.trim();
        }
        if (isNotEmpty(fallback)) {
            return fallback.trim();
        }
        return "";
    }

    /** 金额择优：primary 非空用 primary，否则 fallback。 */
    private BigDecimal firstNonNull(BigDecimal primary, BigDecimal fallback) {
        if (primary != null) {
            return primary;
        }
        return fallback;
    }
}
