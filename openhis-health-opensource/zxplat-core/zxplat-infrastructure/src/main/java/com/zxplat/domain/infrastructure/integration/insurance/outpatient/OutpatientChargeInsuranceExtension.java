/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.outpatient;

import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeConfirmExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeCorrectSettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleBatchExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRebindSettlementPaymentExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRevokeSettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRevokeFeeDetailExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleListExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeConfirmExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleBatchExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeRevokeSettleExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeRevokeFeeDetailExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeSettlementRecordExtensionOut;

import java.util.Collections;
import java.util.List;

/**
 * 门诊收费 ↔ 第三方医保结算扩展点（health 与省份 Module 的唯一边界）。
 *
 * <p><b>调用方</b>：{@code OutpatientChargeAppService}、{@code OutpatientRegistrationAppService}（挂号费）、
 * {@code OutpatientRefundAppService}、{@code OutpatientChargeInsuranceCompensationOrchestration}。</p>
 *
 * <p><b>吉林实现</b>：{@code OutpatientChargeInsuranceExtensionImpl}（module-chs-jilin-bridge）
 * → {@code ChsOutpatientSettlementFacade} → {@code ChsOutpatientSettlementFacadeImpl}。</p>
 *
 * <p><b>字段映射（health 中性名 → 平台/chs-jilin）</b>：{@code payerCertType}→mdtrt_cert_type、
 * {@code insuredPersonNo}→psn_no、{@code chargeBatchNo}→chrg_bchno、
 * {@code encounterPlatformId}→mdtrt_id、{@code settlementId}→setl_id。</p>
 *
 * <p><b>infno 对照（吉林）</b>：</p>
 * <ul>
 *   <li>{@link #preSettle} — 2203→2204→2206</li>
 *   <li>{@link #preSettleBatch} — 多组 2203→2204→2206（慢病混合结算）</li>
 *   <li>{@link #confirmBeforeLocalSettle} — 2207</li>
 *   <li>{@link #revokeSettle} — 2208</li>
 *   <li>{@link #correctSettleTransaction} — 2601</li>
 * </ul>
 */
public interface OutpatientChargeInsuranceExtension {

    /**
     * 是否已装配第三方结算 Module（吉林：{@code zxplat.modules.chs.enabled=true} 且 Client Bean 存在）。
     *
     * @return false 时 AppService 走纯自费路径
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 费用性质是否走第三方医保（吉林：fin_contract 在 chs-contract YAML 有 bus_no）。
     *
     * @param contractId {@code fin_contract.id}
     * @return true 时展示医保读卡/预结算入口
     */
    default boolean supportsThirdPartyContract(Long contractId) {
        return false;
    }

    /**
     * 单账户/单病种预结算（2203→2204→2206）。
     *
     * @param command 就诊、收费行、读卡字段、paymentDraftId
     * @return 含 chargeBatchNo 与 fund/acct/cash 分摊，供收银草稿刷新 displayAmount
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default OutpatientChargePreSettleExtensionOut preSettle(OutpatientChargePreSettleExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 混合预结算（按 accountId|medType|diseCodg 分组）。
     *
     * <p>收银门禁：医保结算组数须 ≤ 1（自费 rollup 可同批）。同一就诊多组 2204 后，
     * 吉林 2206 会按整张未结算明细核对总额。</p>
     *
     * @param command 全部 chargeItemIds，Client 内部分组；任一组失败触发 2205 补偿
     * @return groups + selfPayRollup + totalPsnCashPay
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default OutpatientChargePreSettleBatchExtensionOut preSettleBatch(OutpatientChargePreSettleBatchExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 预览医保结算组数（按 accountId|medType|diseCodg 分组，不出站）。
     *
     * <p>供 AppService 在调 2206 前拦截多病种合批；未装配 Module 时返回 0。</p>
     *
     * @param encounterId   就诊主键
     * @param chargeItemIds 待预结算收费项
     * @return 医保结算组数；无医保项或未装配时为 0
     */
    default int countInsuranceSettleGroups(Long encounterId, List<Long> chargeItemIds) {
        return 0;
    }

    /**
     * 本地收银落账前调用 2207 正式结算（health 管钱、CHS 管证）。
     *
     * @param command paymentDraftId + chargeBatchNo（混合结算逐组 confirm）
     * @return setl_id 与分摊金额，供 fin_payment_rec_detail 写入基金/个账行
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default OutpatientChargeConfirmExtensionOut confirmBeforeLocalSettle(OutpatientChargeConfirmExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 查询支付草稿下仍 ACTIVE 的预结算批次（解析待 confirm 的 chrg_bchno 列表）。
     *
     * @param paymentDraftId {@code fin_payment_draft.id}
     * @return 有效批次号快照；无则空列表
     */
    default List<OutpatientChargePreSettleQueryExtensionOut> listActivePreSettlesByPaymentDraft(
            OutpatientChargePreSettleListExtensionIn query) {
        return Collections.emptyList();
    }

    /**
     * 查询支付单下未撤销的第三方正式结算回执（退费/补偿用）。
     *
     * @param paymentId {@code fin_payment.id}
     * @return {@code yb_settlement_record} 摘要列表
     */
    default List<OutpatientChargeSettlementRecordExtensionOut> listSettlementsByPayment(Long paymentId) {
        return Collections.emptyList();
    }

    /**
     * 将已确认回执（及关联预结算）改挂到新支付草稿；无网关出站。
     *
     * @param command 回执主键列表 + 新支付草稿 id
     * @return 实际改挂条数；须与请求条数一致，否则调用方应失败
     * @throws com.zxplat.framework.core.application.exception.BusinessException 任一回执未能改挂时
     */
    default int rebindConfirmedSettlementsToPayment(OutpatientChargeRebindSettlementPaymentExtensionIn command) {
        return 0;
    }

    /**
     * 撤销第三方正式结算（退费前 2208；部分成功失败时由 Compensation 调用）。
     *
     * @param command settlementRecordId 或 paymentId/preSettleId/settlementId 定位回执
     * @return 被撤销的 setl_id；未装配时抛异常
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default OutpatientChargeRevokeSettleExtensionOut revokeSettle(OutpatientChargeRevokeSettleExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 撤销未正式结算的费用明细（2205；待挂号撤销 / 预结算回滚）。
     */
    default OutpatientChargeRevokeFeeDetailExtensionOut revokeFeeDetails(OutpatientChargeRevokeFeeDetailExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 2601 冲正原 2207 交易（2208 失败或平台超时时的补偿回退）。
     *
     * @param command settlementRecordId + originalInfno（默认 2207）
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default void correctSettleTransaction(OutpatientChargeCorrectSettleExtensionIn command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }
}
