/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.wi.domain.share.WorkInjuryConstants;
import com.openhis.chs.domain.settlement.YbPreSettleStatus;
import com.openhis.wi.domain.settle.command.WiSettleContext;
import com.openhis.wi.domain.entity.YbWiPreSettle;
import com.openhis.wi.domain.repository.YbWiPreSettleRepository;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
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
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
@RequiredArgsConstructor
public class YbWiPreSettleDomainService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final YbWiPreSettleRepository preSettleRepository;

    /**
     * 【2206】预结算成功后写入 ACTIVE 快照。
     *
     * @param ctx            HIS 结算上下文
     * @param mdtrtId        2206 出参 mdtrt_id
     * @param medfeeSumamt   医疗费总额
     * @param fundPaySumamt  统筹支付
     * @param psnCashPay     个人现金
     * @param snapshotJson   2206 出参 JSON
     * @param tenantId       租户 ID（可空，取当前租户）
     * @return 新行；ctx 或 mdtrtId 无效时返回 null
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
        if (ctx == null || isEmpty(mdtrtId)) {
            return null;
        }
        Long resolvedTenantId = resolveTenantId(tenantId);
        YbWiPreSettle row = new YbWiPreSettle();
        row.setContractId(ctx.getContractId());
        row.setEncounterId(ctx.getEncounterId());
        row.setPaymentDraftId(ctx.getPaymentDraftId());
        row.setChrgBchno(newChrgBchno(ctx.getEncounterId(), ctx.getPaymentDraftId()));
        row.setContractBusNo(WorkInjuryConstants.CONTRACT_BUS_NO);
        row.setMdtrtId(mdtrtId.trim());
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
     *
     * @param encounterId    就诊 ID
     * @param paymentDraftId 收银草稿 ID（可空）
     * @return mdtrt_id
     */
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
        return row.map(YbWiPreSettle::getMdtrtId).filter(id -> !isEmpty(id));
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
        String suffix = String.valueOf(Math.abs(seed) % 1_000_000L);
        while (suffix.length() < 6) {
            suffix = "0" + suffix;
        }
        String batch = "WI" + day + suffix;
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
