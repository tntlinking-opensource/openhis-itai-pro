/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.settlement;

import com.openhis.health.integration.insurance.mapper.ChsOutpatientInsuranceIntegrationMapper;
import com.openhis.health.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
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
import com.openhis.whale.chs.jilin.contracts.client.ChsOutpatientSettlementClient;
import com.openhis.whale.chs.jilin.contracts.result.OpInsurancePreSettleResult;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceRevokeFeeDetailResult;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceRevokeSettleResult;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceSettleRecordResult;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 门诊收银 ↔ 吉林医保结算的 Extension 桥接层（health 唯一感知的医保入口）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li><b>本类</b>：health 中性 Command/Result ↔ chs-jilin contracts DTO 的字段映射，不含平台报文</li>
 *   <li>{@link ChsOutpatientSettlementClient}：加载 HIS 上下文、委托 {@code ChsOutpatientSettleAppService}</li>
 *   <li>平台 infno 编排（2201→2204→2206→2207 等）在 application 层，health 不可见</li>
 * </ul>
 *
 * <p>对应 AS：{@code OutpatientChargeSettlementAppService} 通过 Extension 调用本类。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(ChsOutpatientSettlementClient.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class OutpatientChargeInsuranceExtensionImpl implements OutpatientChargeInsuranceExtension {

    private final ChsOutpatientSettlementClient chsOutpatientSettlementClient;

    /**
     * CHS 模块已启用且 Bean 存在时，门诊收银始终走医保 Extension。
     *
     * @return 固定 {@code true}（禁用由 {@code openhis.modules.chs.enabled=false} 控制）
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 判断费用性质是否配置了吉林医保契约（决定是否展示医保结算入口）。
     *
     * @param contractId {@code fin_contract.id}
     * @return 契约在 chs-contract YAML 中有对应 bus_no 时为 true
     */
    @Override
    public boolean supportsThirdPartyContract(Long contractId) {
        return chsOutpatientSettlementClient.isInsuranceContract(contractId);
    }

    /**
     * 单笔门诊医保预结算（IF-OPC-06 → 2203→2204→2206）。
     *
     * @param command health 侧预结算命令（就诊、收费行、读卡字段）
     * @return 含 chrg_bchno、分摊金额的中性结果，供收银展示
     */
    @Override
    public OutpatientChargePreSettleResult preSettle(OutpatientChargePreSettleCommand command) {
        return ChsOutpatientInsuranceIntegrationMapper.toNeutralPreSettleResult(
                chsOutpatientSettlementClient.preSettle(
                        ChsOutpatientInsuranceIntegrationMapper.toChsPreSettleCommand(command)));
    }

    /**
     * 多病种/多账户混合预结算（慢病多科室场景，按病种拆 chrg_bchno 分批 2206）。
     *
     * @param command 含全部 chargeItemIds，Port 内部分组
     * @return 各组预结算结果 + 自费汇总行
     */
    @Override
    public OutpatientChargePreSettleBatchResult preSettleBatch(OutpatientChargePreSettleBatchCommand command) {
        return ChsOutpatientInsuranceIntegrationMapper.toNeutralPreSettleBatchResult(
                chsOutpatientSettlementClient.preSettleBatch(
                        ChsOutpatientInsuranceIntegrationMapper.toChsPreSettleBatchCommand(command)));
    }

    /**
     * 本地收银确认前调用 2207 正式结算（health 管钱、CHS 管证）。
     *
     * <p>须在 HIS 落账前完成，以获取 setl_id 与基金分项写入 {@code yb_settlement_record}。</p>
     *
     * @param command 含 paymentDraftId、chrg_bchno
     * @return 平台结算凭证 id 与四类分摊金额
     */
    @Override
    public OutpatientChargeConfirmResult confirmBeforeLocalSettle(OutpatientChargeConfirmCommand command) {
        return ChsOutpatientInsuranceIntegrationMapper.toNeutralConfirmResult(
                chsOutpatientSettlementClient.confirmBeforeLocalSettle(
                        ChsOutpatientInsuranceIntegrationMapper.toChsConfirmCommand(command)));
    }

    /**
     * 查询收银草稿下仍有效的预结算快照（ACTIVE 状态，用于撤销/重算判断）。
     *
     * @param paymentDraftId {@code fin_payment_draft.id}
     * @return 仅含 chrg_bchno 的轻量快照列表
     */
    @Override
    public List<OutpatientChargePreSettleSnapshot> listActivePreSettlesByPaymentDraft(Long paymentDraftId) {
        List<OpInsurancePreSettleResult> active =
            chsOutpatientSettlementClient.listActivePreSettlesByPaymentDraft(paymentDraftId);
        List<OutpatientChargePreSettleSnapshot> snapshots = new ArrayList<>();
        for (OpInsurancePreSettleResult row : active) {
            if (StringUtils.isBlank(row.getChrgBchno())) {
                continue;
            }
            snapshots.add(new OutpatientChargePreSettleSnapshot()
                .setChargeBatchNo(row.getChrgBchno().trim())
                .setInsuredPersonNo(row.getPsnNo())
                .setInsuplcAdmdvs(row.getInsuplcAdmdvs())
                .setInsutype(row.getInsutype())
                .setPayerCertType(row.getMdtrtCertType())
                .setAcctUsedFlag(row.getAcctUsedFlag()));
        }
        return snapshots;
    }

    /**
     * 按 payment 查询已落库的医保结算回执（补偿/对账/撤销入口）。
     *
     * @param paymentId {@code fin_payment.id}
     * @return 含 setl_id、msgid2207 的回执列表
     */
    @Override
    public List<OutpatientChargeSettlementRecordResult> listSettlementsByPayment(Long paymentId) {
        List<OpInsuranceSettleRecordResult> records =
            chsOutpatientSettlementClient.listSettlementsByPayment(paymentId);
        List<OutpatientChargeSettlementRecordResult> results = new ArrayList<>();
        for (OpInsuranceSettleRecordResult row : records) {
            if (row == null) {
                continue;
            }
            results.add(ChsOutpatientInsuranceIntegrationMapper.toNeutralSettlementRecord(row));
        }
        return results;
    }

    /**
     * 撤销门诊正式结算（2208），用于退费/冲正前回滚平台凭证。
     *
     * @param command 含 settlementRecordId 或 paymentId 或 setlId 之一
     * @return 被撤销的回执 id；contracts 返回 null 时给出空结果（不抛异常）
     */
    @Override
    public OutpatientChargeRevokeSettleResult revokeSettle(OutpatientChargeRevokeSettleCommand command) {
        OpInsuranceRevokeSettleResult chsResult = chsOutpatientSettlementClient.revokeSettle(
                ChsOutpatientInsuranceIntegrationMapper.toChsRevokeSettleCommand(command));
        if (chsResult == null) {
            return new OutpatientChargeRevokeSettleResult();
        }
        return ChsOutpatientInsuranceIntegrationMapper.toNeutralRevokeSettleResult(chsResult);
    }

    @Override
    public OutpatientChargeRevokeFeeDetailResult revokeFeeDetails(OutpatientChargeRevokeFeeDetailCommand command) {
        OpInsuranceRevokeFeeDetailResult chsResult = chsOutpatientSettlementClient.revokeFeeDetails(
            ChsOutpatientInsuranceIntegrationMapper.toChsRevokeFeeDetailCommand(command));
        OutpatientChargeRevokeFeeDetailResult result = new OutpatientChargeRevokeFeeDetailResult();
        if (chsResult != null) {
            result.setChargeBatchNo(chsResult.getChrgBchno());
            result.setRevoked(chsResult.isRevoked());
        }
        return result;
    }

    /**
     * 2601 冲正原交易（2207/2208 超时补偿，由 PaymentSettledInsuranceListener 触发）。
     *
     * @param command 含 settlementRecordId 与原 infno
     */
    @Override
    public void correctSettleTransaction(OutpatientChargeCorrectSettleCommand command) {
        chsOutpatientSettlementClient.correctSettleTransaction(
                ChsOutpatientInsuranceIntegrationMapper.toChsCorrectSettleCommand(command));
    }
}
