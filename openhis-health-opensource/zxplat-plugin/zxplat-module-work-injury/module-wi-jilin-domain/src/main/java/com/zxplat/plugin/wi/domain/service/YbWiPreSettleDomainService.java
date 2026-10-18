/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.service;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.zxplat.plugin.wi.domain.share.WorkInjuryConstants;
import com.zxplat.plugin.wi.domain.share.WiErrorCodes;
import com.zxplat.plugin.chs.domain.settlement.YbPreSettleStatus;
import com.zxplat.plugin.wi.domain.settle.command.WiSettleContext;
import com.zxplat.plugin.wi.domain.entity.YbWiPreSettle;
import com.zxplat.plugin.wi.domain.repository.YbWiPreSettleRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** 2206 预结算快照 {@code yb_wi_pre_settle} 写侧（ACTIVE → SETTLED / REVOKED）。 */
@Service
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class YbWiPreSettleDomainService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final YbWiPreSettleRepository preSettleRepository;

    /**
     * 【2206】预结算成功后写入 ACTIVE 快照。
     * <p>院点 NEUSI 实回 {@code mdtrt_id} 常为空串，但 2207 规则强制非空（手册：传入实际单据号）。
     * 空时用本地 {@code chrg_bchno} 作为单据号落库，供 2207 回传。</p>
     *
     * @param ctx            HIS 结算上下文
     * @param mdtrtId        2206 出参 mdtrt_id（可空）
     * @param medfeeSumamt   医疗费总额
     * @param fundPaySumamt  统筹支付
     * @param psnCashPay     个人现金
     * @param snapshotJson   2206 出参 JSON
     * @param tenantId       租户 ID（可空，取当前租户）
     * @return 新行
     */
    @Transactional
    public YbWiPreSettle saveActiveFrom2206(
            WiSettleContext ctx,
            String mdtrtId,
            BigDecimal medfeeSumamt,
            BigDecimal fundPaySumamt,
            BigDecimal psnCashPay,
            String snapshotJson,
            Long tenantId) {
        if (ctx == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "2206 缺少 HIS 上下文，无法写预结算快照");
        }
        Long resolvedTenantId = resolveTenantId(tenantId);
        String chrgBchno = newChrgBchno(ctx.getEncounterId(), ctx.getPaymentDraftId());
        String resolvedMdtrt = mdtrtId == null ? "" : mdtrtId.trim();
        if (isEmpty(resolvedMdtrt)) {
            // NEUSILOGS：2206 output.mdtrt_id=""；2207 报「mdtrt_id不允许为空」
            resolvedMdtrt = chrgBchno;
        }
        YbWiPreSettle row = new YbWiPreSettle();
        row.setContractId(ctx.getContractId());
        row.setEncounterId(ctx.getEncounterId());
        row.setPaymentDraftId(ctx.getPaymentDraftId());
        row.setChrgBchno(chrgBchno);
        row.setContractBusNo(WorkInjuryConstants.CONTRACT_BUS_NO);
        row.setMdtrtId(resolvedMdtrt);
        String psnNo = "";
        if (ctx.getPsnNo() != null) {
            psnNo = ctx.getPsnNo().trim();
        }
        row.setPsnNo(psnNo);
        row.setMedfeeSumamt(firstNonNull(medfeeSumamt, ctx.getMedfeeSumamt()));
        row.setFundPaySumamt(fundPaySumamt);
        row.setAcctPay(BigDecimal.ZERO);
        row.setPsnCashPay(psnCashPay);
        row.setStatus(YbPreSettleStatus.ACTIVE.name());
        row.setSnapshotJson(snapshotJson);
        row.setTenantId(resolvedTenantId);
        return preSettleRepository.save(row);
    }

    /**
     * 读 ACTIVE 快照的 mdtrt_id（2207 assemble 挂接）。
     * <p>历史行若 mdtrt_id 为空，回退 {@code chrg_bchno} 并回写，避免已预结算单据无法确认收费。</p>
     *
     * @param encounterId    就诊 ID
     * @param paymentDraftId 收银草稿 ID（可空）
     * @return mdtrt_id
     */
    @Transactional
    public Optional<String> findActiveMdtrtId(Long encounterId, Long paymentDraftId) {
        if (encounterId == null) {
            return Optional.empty();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        Optional<YbWiPreSettle> row;
        if (paymentDraftId != null) {
            row = preSettleRepository
                    .findFirstByEncounterIdAndPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                            encounterId, paymentDraftId, YbPreSettleStatus.ACTIVE.name(), tenantId);
        } else {
            row = preSettleRepository
                    .findFirstByEncounterIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                            encounterId, YbPreSettleStatus.ACTIVE.name(), tenantId);
        }
        if (row.isEmpty()) {
            return Optional.empty();
        }
        YbWiPreSettle pre = row.get();
        String id = pre.getMdtrtId();
        if (!isEmpty(id)) {
            return Optional.of(id.trim());
        }
        String fallback = pre.getChrgBchno();
        if (isEmpty(fallback)) {
            return Optional.empty();
        }
        pre.setMdtrtId(fallback.trim());
        preSettleRepository.save(pre);
        return Optional.of(fallback.trim());
    }

    /**
     * 【2207】成功后 ACTIVE → SETTLED。
     *
     * @param encounterId    就诊 ID
     * @param paymentDraftId 收银草稿 ID（可空）
     */
    @Transactional
    public void markSettled(Long encounterId, Long paymentDraftId) {
        if (encounterId == null) {
            return;
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        Optional<YbWiPreSettle> row;
        if (paymentDraftId != null) {
            row = preSettleRepository
                    .findFirstByEncounterIdAndPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                            encounterId, paymentDraftId, YbPreSettleStatus.ACTIVE.name(), tenantId);
        } else {
            row = preSettleRepository
                    .findFirstByEncounterIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                            encounterId, YbPreSettleStatus.ACTIVE.name(), tenantId);
        }
        row.ifPresent(pre -> {
            pre.setStatus(YbPreSettleStatus.SETTLED.name());
            preSettleRepository.save(pre);
        });
    }

    /**
     * 【2208】撤销后预结算快照 → REVOKED。
     *
     * @param preSettleId yb_wi_pre_settle.id
     */
    @Transactional
    public void markRevokedByPreSettleId(Long preSettleId) {
        if (preSettleId == null) {
            return;
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        preSettleRepository.findById(preSettleId)
                .filter(pre -> tenantId.equals(pre.getTenantId()))
                .ifPresent(pre -> {
                    pre.setStatus(YbPreSettleStatus.REVOKED.name());
                    preSettleRepository.save(pre);
                });
    }

    /**
     * 按 paymentDraft 查 ACTIVE 预结算行（收银刷新金额用）。
     *
     * @param paymentDraftId 收银草稿 ID
     * @param tenantId       租户 ID
     * @return 预结算行
     */
    public Optional<YbWiPreSettle> findActiveByPaymentDraft(Long paymentDraftId, Long tenantId) {
        if (paymentDraftId == null) {
            return Optional.empty();
        }
        return preSettleRepository
                .findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                        paymentDraftId, YbPreSettleStatus.ACTIVE.name(), tenantId);
    }

    /**
     * 收银刷新：优先 ACTIVE；单边补落后仅有 SETTLED 时也可读金额。
     *
     * @param paymentDraftId 收银草稿 ID
     * @param tenantId       租户 ID
     * @return 预结算行
     */
    public Optional<YbWiPreSettle> findActiveOrSettledByPaymentDraft(Long paymentDraftId, Long tenantId) {
        Optional<YbWiPreSettle> active = findActiveByPaymentDraft(paymentDraftId, tenantId);
        if (active.isPresent()) {
            return active;
        }
        if (paymentDraftId == null) {
            return Optional.empty();
        }
        return preSettleRepository
                .findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                        paymentDraftId, YbPreSettleStatus.SETTLED.name(), tenantId);
    }

    /**
     * 就诊下最新可补账单边的 ACTIVE 预结算（须有单据号）。
     *
     * @param encounterId 就诊 ID
     * @param tenantId    租户 ID
     * @return ACTIVE 且 mdtrt/chrg 非空的预结算
     */
    public Optional<YbWiPreSettle> findRecoverableActiveByEncounter(Long encounterId, Long tenantId) {
        if (encounterId == null) {
            return Optional.empty();
        }
        Long resolvedTenantId = tenantId != null ? tenantId : TenantUtils.getCurrentTenantId();
        Optional<YbWiPreSettle> row = preSettleRepository
                .findFirstByEncounterIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                        encounterId, YbPreSettleStatus.ACTIVE.name(), resolvedTenantId);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        YbWiPreSettle pre = row.get();
        if (isEmpty(pre.getMdtrtId()) && isEmpty(pre.getChrgBchno())) {
            return Optional.empty();
        }
        return row;
    }

    /**
     * 单边补账时把 ACTIVE 预结算挂到当前收银草稿。
     *
     * @param pre            预结算行
     * @param paymentDraftId 新草稿 ID
     * @return 更新后的行
     */
    @Transactional
    public YbWiPreSettle rebindPaymentDraft(YbWiPreSettle pre, Long paymentDraftId) {
        if (pre == null || paymentDraftId == null) {
            return pre;
        }
        if (paymentDraftId.equals(pre.getPaymentDraftId())) {
            return pre;
        }
        pre.setPaymentDraftId(paymentDraftId);
        return preSettleRepository.save(pre);
    }

    /**
     * 生成 WI 收费批次号：{@code WI}{yyyyMMdd}{6位 encounter⊕draft 哈希}，最长 30。
     *
     * @param encounterId    就诊 id
     * @param paymentDraftId 收银草稿 id
     * @return chrg_bchno
     */
    private String newChrgBchno(Long encounterId, Long paymentDraftId) {
        String day = LocalDate.now().format(DAY);
        long seed = 0L;
        if (encounterId != null) {
            seed = encounterId;
        }
        if (paymentDraftId != null) {
            seed = seed ^ paymentDraftId;
        }
        String suffix = String.valueOf(Math.abs(seed) % 100_000L);
        while (suffix.length() < 5) {
            suffix = "0" + suffix;
        }
        // 附加时间片，避免同日同草稿重预结算复用单据号触发平台「已使用」
        String time = String.valueOf(System.currentTimeMillis() % 10_000L);
        while (time.length() < 4) {
            time = "0" + time;
        }
        String batch = "WI" + day + suffix + time;
        if (batch.length() > 30) {
            return batch.substring(0, 30);
        }
        return batch;
    }

    /** 租户 id 回退：参数空则取当前租户。 */
    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }

    /** 金额择优：primary 非空用 primary，否则 fallback。 */
    private BigDecimal firstNonNull(BigDecimal primary, BigDecimal fallback) {
        if (primary != null) {
            return primary;
        }
        return fallback;
    }
}
