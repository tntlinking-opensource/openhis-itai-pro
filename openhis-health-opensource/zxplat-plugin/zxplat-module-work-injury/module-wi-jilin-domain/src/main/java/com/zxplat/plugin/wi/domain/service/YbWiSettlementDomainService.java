/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.service;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.settlement.constants.ChsSettleEncounterClass;
import com.zxplat.plugin.chs.domain.settlement.YbPreSettleStatus;
import com.zxplat.plugin.chs.domain.settlement.YbSettlementVoucherStatus;
import com.zxplat.plugin.chs.domain.settlement.entity.YbSettlementRecord;
import com.zxplat.plugin.chs.domain.settlement.repository.YbSettlementRecordRepository;
import com.zxplat.plugin.chs.domain.settlement.service.YbSettlementReversalDomainService;
import com.zxplat.plugin.wi.domain.share.WorkInjuryConstants;
import com.zxplat.plugin.wi.domain.share.WiErrorCodes;
import com.zxplat.plugin.wi.domain.entity.YbWiPreSettle;
import com.zxplat.plugin.wi.domain.repository.YbWiPreSettleRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
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
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class YbWiSettlementDomainService {

    private final YbWiPreSettleRepository preSettleRepository;
    private final YbSettlementRecordRepository settlementRecordRepository;
    private final YbSettlementReversalDomainService settlementReversalDomainService;

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
     * @param psnName           患者姓名（可空）
     * @param platformSetlTime  2207 出参 setl_time（可空，回落当前时间）
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
            String psnName,
            LocalDateTime platformSetlTime,
            Long tenantId) {
        if (isEmpty(setlId)) {
            // 院点 2207 出参常无 setl_id（NEUSILOGS 与 2206 同结构），用单据号作结算主键
            setlId = firstNonBlank(mdtrtId, chrgBchno);
        }
        if (isEmpty(setlId)) {
            throw new BusinessException(WiErrorCodes.PLATFORM_INFCODE_FAILED, "2207 出参缺少 setl_id/mdtrt_id");
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
        if (isNotEmpty(psnName)) {
            row.setPsnName(psnName.trim());
        }
        row.setFundBreakdownJson(fundBreakdownJson);
        row.setTenantId(resolvedTenantId);
        row.setSetlTime(platformSetlTime != null ? platformSetlTime : LocalDateTime.now());
        row.setCreatedAt(LocalDateTime.now());
        return settlementRecordRepository.saveAndFlush(row);
    }

    /**
     * 平台 2207 已成功、本地无回执时的单边补落。
     * <p>把就诊下带单据号的 ACTIVE 预结算挂到当前草稿，写入 settlement_record 并标 SETTLED。</p>
     * <p>若改绑到与原回执不同的收银草稿，须 {@code currentChargeTotal} 与回执医疗费一致，
     * 否则拒绝（防止新开收费复用旧 2207 后被收银按差额补现金）。</p>
     *
     * @param encounterId         就诊 ID
     * @param paymentDraftId      当前收银草稿
     * @param contractId          合同（可空）
     * @param tenantId            租户
     * @param currentChargeTotal  本次待结算收费项合计（可空：仅允许同草稿补落）
     * @return 补落后的回执；无可补数据时 empty
     */
    @Transactional
    public Optional<YbSettlementRecord> recoverUnilateralFromActivePreSettle(
            Long encounterId,
            Long paymentDraftId,
            Long contractId,
            Long tenantId,
            BigDecimal currentChargeTotal) {
        if (encounterId == null || paymentDraftId == null) {
            return Optional.empty();
        }
        Long resolvedTenantId = resolveTenantId(tenantId);
        Optional<YbSettlementRecord> already = findByPaymentDraft(paymentDraftId, null, resolvedTenantId);
        if (already.isPresent()) {
            if (currentChargeTotal != null) {
                assertReusableAgainstChargeTotal(
                        already.get().getMedfeeSumamt(), currentChargeTotal, paymentDraftId);
            }
            return already;
        }
        Optional<YbWiPreSettle> preOpt = preSettleRepository
                .findFirstByEncounterIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                        encounterId, YbPreSettleStatus.ACTIVE.name(), resolvedTenantId);
        if (preOpt.isEmpty()) {
            // 可能已 SETTLED 但 settlement 丢失：再按 SETTLED+单据号补
            preOpt = preSettleRepository
                    .findFirstByEncounterIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                            encounterId, YbPreSettleStatus.SETTLED.name(), resolvedTenantId);
        }
        if (preOpt.isEmpty()) {
            return Optional.empty();
        }
        YbWiPreSettle pre = preOpt.get();
        String mdtrtId = firstNonBlank(pre.getMdtrtId(), pre.getChrgBchno());
        if (isEmpty(mdtrtId)) {
            return Optional.empty();
        }
        Optional<YbSettlementRecord> bySetl = settlementRecordRepository
                .findFirstBySetlIdAndTenantIdAndIsDeletedFalse(mdtrtId.trim(), resolvedTenantId);
        if (bySetl.isPresent()) {
            YbSettlementRecord existing = bySetl.get();
            assertMayRebindDraft(pre, existing, paymentDraftId, currentChargeTotal);
            if (!paymentDraftId.equals(pre.getPaymentDraftId())) {
                pre.setPaymentDraftId(paymentDraftId);
                preSettleRepository.save(pre);
            }
            return bySetl;
        }
        assertMayRebindDraft(pre, null, paymentDraftId, currentChargeTotal);
        if (!paymentDraftId.equals(pre.getPaymentDraftId())) {
            pre.setPaymentDraftId(paymentDraftId);
            preSettleRepository.save(pre);
        }
        // saveConfirmedFrom2207 要求 ACTIVE；若已 SETTLED 先临时改回 ACTIVE
        boolean wasSettled = YbPreSettleStatus.SETTLED.name().equals(pre.getStatus());
        if (wasSettled) {
            pre.setStatus(YbPreSettleStatus.ACTIVE.name());
            preSettleRepository.save(pre);
        }
        YbSettlementRecord record = saveConfirmedFrom2207(
                encounterId,
                contractId != null ? contractId : pre.getContractId(),
                paymentDraftId,
                mdtrtId,
                mdtrtId,
                pre.getPsnNo(),
                pre.getChrgBchno(),
                pre.getMedfeeSumamt(),
                pre.getFundPaySumamt(),
                pre.getPsnCashPay(),
                pre.getSnapshotJson(),
                null,
                null,
                null,
                resolvedTenantId);
        pre.setStatus(YbPreSettleStatus.SETTLED.name());
        preSettleRepository.save(pre);
        return Optional.of(record);
    }

    /**
     * 改绑收银草稿前校验：同草稿可补落；异草稿须本次收费合计与回执医疗费一致。
     */
    private void assertMayRebindDraft(
            YbWiPreSettle pre,
            YbSettlementRecord existing,
            Long paymentDraftId,
            BigDecimal currentChargeTotal) {
        boolean sameDraft = paymentDraftId.equals(pre.getPaymentDraftId())
                || (existing != null && paymentDraftId.equals(existing.getPaymentId()));
        BigDecimal expected = existing != null && existing.getMedfeeSumamt() != null
                ? existing.getMedfeeSumamt()
                : pre.getMedfeeSumamt();
        if (sameDraft) {
            if (currentChargeTotal != null) {
                assertReusableAgainstChargeTotal(expected, currentChargeTotal, paymentDraftId);
            }
            return;
        }
        if (currentChargeTotal == null) {
            throw new BusinessException(
                    WiErrorCodes.SETTLEMENT_REUSE_FORBIDDEN,
                    "本就诊已有工伤结算回执，拒绝改绑到其他收银草稿（paymentDraftId="
                            + paymentDraftId + "）。请先撤销原结算(2208)后再重新结算。");
        }
        assertReusableAgainstChargeTotal(expected, currentChargeTotal, paymentDraftId);
    }

    /**
     * 本次收费合计须与已有回执医疗费一致。
     */
    private static void assertReusableAgainstChargeTotal(
            BigDecimal expectedMedfee,
            BigDecimal currentChargeTotal,
            Long paymentDraftId) {
        BigDecimal expected = scale2(expectedMedfee);
        BigDecimal actual = scale2(currentChargeTotal);
        if (expected.compareTo(actual) != 0) {
            throw new BusinessException(
                    WiErrorCodes.SETTLEMENT_REUSE_FORBIDDEN,
                    "本就诊已有工伤结算回执（医疗费 " + expected + "），与本次收费合计 "
                            + actual + " 不一致，禁止复用回执（paymentDraftId=" + paymentDraftId
                            + "）。请先撤销原结算(2208)后再重新结算，或分次结算。");
        }
    }

    private static BigDecimal scale2(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 【2208】结算回执标记 REVOKED，并合成 {@code yb_settlement_reversal}（对账抵减用）。
     *
     * @param settlementRecordId yb_settlement_record.id
     * @param tenantId           租户 ID
     * @return 更新后的回执；已 REVOKED 时幂等返回（仍补写缺失撤销凭证）
     */
    @Transactional
    public YbSettlementRecord markRevoked2208(Long settlementRecordId, Long tenantId) {
        return markRevoked2208(settlementRecordId, tenantId, null);
    }

    /**
     * 【2208】结算回执标记 REVOKED，并合成 {@code yb_settlement_reversal}（对账抵减用）。
     *
     * @param settlementRecordId yb_settlement_record.id
     * @param tenantId           租户 ID
     * @param invokeLogId        2208 invoke 日志 ID（可空）
     * @return 更新后的回执；已 REVOKED 时幂等返回（仍补写缺失撤销凭证）
     */
    @Transactional
    public YbSettlementRecord markRevoked2208(Long settlementRecordId, Long tenantId, Long invokeLogId) {
        if (settlementRecordId == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "settlementRecordId 不能为空");
        }
        Long resolvedTenantId = resolveTenantId(tenantId);
        YbSettlementRecord row = settlementRecordRepository
                .findFirstByIdAndTenantIdAndIsDeletedFalse(settlementRecordId, resolvedTenantId)
                .orElseThrow(() -> new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "结算回执不存在"));
        // 工伤 2208 无平台 setlinfo：按回执快照合成撤销凭证，供结算统计同窗抵减
        settlementReversalDomainService.persistSyntheticIfAbsent(row, null, invokeLogId);
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
