/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.outpatient;

import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargeConfirmCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargeCorrectSettleCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargePreSettleBatchCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargePreSettleCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargeRevokeSettleCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargeRevokeFeeDetailCommand;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargeConfirmResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargePreSettleBatchResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargePreSettleResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargePreSettleSnapshot;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargeRevokeSettleResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargeRevokeFeeDetailResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargeSettlementRecordResult;

import java.util.Collections;
import java.util.List;

/**
 * 门诊收费 ↔ 第三方医保结算扩展点（health 与省份 Module 的唯一边界）。
 *
 * <p><b>调用方</b>：{@code OutpatientChargeAppService}、{@code OutpatientRegistrationAppService}（挂号费）、
 * {@code OutpatientRefundAppService}、{@code OutpatientInsuranceSettlementCompensation}（integration.support.insurance.outpatient）。</p>
 *
 * <p><b>吉林实现</b>：{@code OutpatientChargeInsuranceExtensionImpl}（chs-jilin-adapter）
 * → {@code ChsOutpatientSettlementClient} → {@code ChsOutpatientSettleAppService}。</p>
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
     * 是否已装配第三方结算 Module（吉林：{@code openhis.modules.chs.enabled=true} 且 Port Bean 存在）。
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
    default OutpatientChargePreSettleResult preSettle(OutpatientChargePreSettleCommand command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 多账户/多病种混合预结算（按 accountId|medType|diseCodg 分组多次 2206）。
     *
     * @param command 全部 chargeItemIds，Port 内部分组；任一组失败触发 2205 补偿
     * @return groups + selfPayRollup + totalPsnCashPay
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default OutpatientChargePreSettleBatchResult preSettleBatch(OutpatientChargePreSettleBatchCommand command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 本地收银落账前调用 2207 正式结算（health 管钱、CHS 管证）。
     *
     * @param command paymentDraftId + chargeBatchNo（混合结算逐组 confirm）
     * @return setl_id 与分摊金额，供 fin_payment_rec_detail 写入基金/个账行
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default OutpatientChargeConfirmResult confirmBeforeLocalSettle(OutpatientChargeConfirmCommand command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 查询支付草稿下仍 ACTIVE 的预结算批次（解析待 confirm 的 chrg_bchno 列表）。
     *
     * @param paymentDraftId {@code fin_payment_draft.id}
     * @return 有效批次号快照；无则空列表
     */
    default List<OutpatientChargePreSettleSnapshot> listActivePreSettlesByPaymentDraft(Long paymentDraftId) {
        return Collections.emptyList();
    }

    /**
     * 查询支付单下未撤销的第三方正式结算回执（退费/补偿用）。
     *
     * @param paymentId {@code fin_payment.id}
     * @return {@code yb_settlement_record} 摘要列表
     */
    default List<OutpatientChargeSettlementRecordResult> listSettlementsByPayment(Long paymentId) {
        return Collections.emptyList();
    }

    /**
     * 撤销第三方正式结算（退费前 2208；部分成功失败时由 Compensation 调用）。
     *
     * @param command settlementRecordId 或 paymentId/preSettleId/settlementId 定位回执
     * @return 被撤销的 setl_id；未装配时抛异常
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default OutpatientChargeRevokeSettleResult revokeSettle(OutpatientChargeRevokeSettleCommand command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 撤销未正式结算的费用明细（2205；待挂号撤销 / 预结算回滚）。
     */
    default OutpatientChargeRevokeFeeDetailResult revokeFeeDetails(OutpatientChargeRevokeFeeDetailCommand command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }

    /**
     * 2601 冲正原 2207 交易（2208 失败或平台超时时的补偿回退）。
     *
     * @param command settlementRecordId + originalInfno（默认 2207）
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default void correctSettleTransaction(OutpatientChargeCorrectSettleCommand command) {
        throw new UnsupportedOperationException("第三方结算未装配");
    }
}
