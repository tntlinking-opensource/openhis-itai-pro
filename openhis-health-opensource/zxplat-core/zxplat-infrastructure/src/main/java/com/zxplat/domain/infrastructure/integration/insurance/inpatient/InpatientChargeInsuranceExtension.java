/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.inpatient;

import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeAdmissionExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeConfirmExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeEncounterExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargePreSettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeRevokeFeeDetailExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeRevokeSettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeAdmissionPlatformQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeUploadFeeBatchExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientPlatformVisitAlignExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeAdmissionPlatformQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeAdmissionExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeConfirmExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargePreSettleExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeRevokeFeeDetailExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeRevokeSettleExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeUploadFeeBatchExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientPlatformVisitAlignExtensionOut;

import java.util.Collections;
import java.util.List;

/**
 * 住院收费 ↔ 第三方医保结算扩展点（health 与省份 Module 的唯一边界）。
 *
 * <p><b>调用方</b>：{@code InpatientSettleAppService}、{@code InpatientRegistrationAppService}。</p>
 *
 * <p><b>吉林实现</b>：{@code InpatientChargeInsuranceExtensionImpl}（module-chs-jilin-bridge）
 * → {@code ChsInpatientSettlementFacade} → {@code ChsInpatientSettlementFacadeImpl}。</p>
 *
 * <p><b>infno 对照（吉林）</b>：</p>
 * <ul>
 *   <li>{@link #ensureAdmission} — 2401</li>
 *   <li>{@link #uploadPendingFeeBatch} — 2301（单批）</li>
 *   <li>{@link #preSettle} — 2301→2303</li>
 *   <li>{@link #confirmBeforeLocalSettle} — 2304</li>
 *   <li>{@link #revokeSettle} — 2305</li>
 *   <li>{@link #revokeFeeDetails} — 2302</li>
 *   <li>{@link #revokeAdmission} — 2404</li>
 *   <li>{@link #revokeDischarge} — 2405</li>
 *   <li>{@link #changeInpatientInfo} — 2403</li>
 * </ul>
 *
 * <p>住院批次键为 {@code IP{preSettleId}}，非平台 chrg_bchno。</p>
 */
public interface InpatientChargeInsuranceExtension {

    /** @see com.zxplat.domain.infrastructure.integration.insurance.outpatient.OutpatientChargeInsuranceExtension#isEnabled() */
    default boolean isEnabled() {
        return false;
    }

    /** @see com.zxplat.domain.infrastructure.integration.insurance.outpatient.OutpatientChargeInsuranceExtension#supportsThirdPartyContract(Long) */
    default boolean supportsThirdPartyContract(Long contractId) {
        return false;
    }

    /**
     * 入院登记/确保平台就诊存在（2401）。
     *
     * @param command 就诊、费用性质、读卡字段
     * @return mdtrt_id；未装配时抛异常
     */
    default InpatientChargeAdmissionExtensionOut ensureAdmission(InpatientChargeAdmissionExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 住院预结算（2301 费用上传 → 2303 预结算）。
     *
     * @param command chargeItemIds、settleMode（DISCHARGE_FULL/MID_SETTLE）
     * @return 分摊金额；同 paymentDraft 重复预结算会取消旧 ACTIVE 快照
     */
    default InpatientChargePreSettleExtensionOut preSettle(InpatientChargePreSettleExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 上传一批待传住院费用明细（2301，平台单次上限 100 行）。
     *
     * <p>供前端进度条分批调用；{@code preferredMdtrtId} 非空时跳过 2401。</p>
     *
     * @param command 读卡字段 + chargeItemIds + 可选 preferredMdtrtId
     * @return 本批进度；未装配时抛异常
     */
    default InpatientChargeUploadFeeBatchExtensionOut uploadPendingFeeBatch(
            InpatientChargeUploadFeeBatchExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 诊断本地平台就诊映射与 5201 在院态是否一致。
     */
    default InpatientPlatformVisitAlignExtensionOut diagnosePlatformVisit(
            InpatientPlatformVisitAlignExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 按诊断结果执行轻量修复（清多余在院 / 软删死映射）。
     */
    default InpatientPlatformVisitAlignExtensionOut repairPlatformVisit(
            InpatientPlatformVisitAlignExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 本地落账前 2304 正式结算。
     *
     * @param command paymentDraftId + preSettleId
     * @return setl_id 供 fin_payment 挂接
     */
    default InpatientChargeConfirmExtensionOut confirmBeforeLocalSettle(InpatientChargeConfirmExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 支付草稿下 ACTIVE 预结算列表（confirm 前解析 preSettleId）。
     *
     * @param paymentDraftId 支付草稿 id
     * @return 按 createdAt 排序的预结算结果
     */
    default List<InpatientChargePreSettleExtensionOut> listActivePreSettlesByPaymentDraft(Long paymentDraftId) {
        return Collections.emptyList();
    }

    /**
     * 支付单关联的已 SETTLED 预结算（查单/退费用）。
     *
     * @param paymentId 支付单 id
     * @return 预结算摘要；无则 null
     */
    default InpatientChargePreSettleExtensionOut findSettledPreSettleByPayment(Long paymentId) {
        return null;
    }

    /** 撤销住院正式结算（2305）。 */
    default InpatientChargeRevokeSettleExtensionOut revokeSettle(InpatientChargeRevokeSettleExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /** 撤销住院费用明细（2302）。 */
    default InpatientChargeRevokeFeeDetailExtensionOut revokeFeeDetails(InpatientChargeRevokeFeeDetailExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 批量查询就诊是否已在平台完成入院登记（2401）。
     *
     * @param encounterIds 住院就诊主键列表
     */
    default List<InpatientChargeAdmissionPlatformQueryExtensionOut> findAdmissionPlatformSnapshots(
            InpatientChargeAdmissionPlatformQueryExtensionIn query) {
        return Collections.emptyList();
    }

    /** 撤销入院登记（2404）；未装配时为 no-op。 */
    default void revokeAdmission(InpatientChargeEncounterExtensionIn command) {
    }

    /** 撤销出院登记（2405）；未装配时为 no-op。 */
    default void revokeDischarge(InpatientChargeEncounterExtensionIn command) {
    }

    /** 变更住院信息（2403）；未装配时为 no-op。 */
    default void changeInpatientInfo(InpatientChargeAdmissionExtensionIn command) {
    }
}
