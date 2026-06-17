/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port;

import com.openhis.whale.chs.jilin.port.model.OpInsuranceConfirmCommand;
import com.openhis.whale.chs.jilin.port.model.OpInsuranceConfirmResult;
import com.openhis.whale.chs.jilin.port.model.OpInsurancePreSettleBatchCommand;
import com.openhis.whale.chs.jilin.port.model.OpInsurancePreSettleBatchResult;
import com.openhis.whale.chs.jilin.port.model.OpInsurancePreSettleCommand;
import com.openhis.whale.chs.jilin.port.model.OpInsurancePreSettleResult;
import com.openhis.whale.chs.jilin.port.model.OpInsuranceRevokeFeeDetailCommand;
import com.openhis.whale.chs.jilin.port.model.OpInsuranceRevokeFeeDetailResult;
import com.openhis.whale.chs.jilin.port.model.OpInsuranceRevokeSettleCommand;
import com.openhis.whale.chs.jilin.port.model.OpInsuranceRevokeSettleResult;
import com.openhis.whale.chs.jilin.port.model.OpInsuranceSettleRecordResult;

import java.util.List;

/**
 * 门诊收费 ↔ 医保结算 Port（同步 MVP：2203→2204→2206 预结算，2207 正式结算）。
 *
 * <p>由 chs-jilin-adapter 实现；health-application 通过 optional 依赖注入。</p>
 */
public interface OutpatientInsuranceSettlementPort {

    /** 合同是否为医保费用性质。 */
    boolean isInsuranceContract(Long contractId);

    /** 单组 2203→2204→2206 预结算。 */
    OpInsurancePreSettleResult preSettle(OpInsurancePreSettleCommand command);

    /**
     * 混合预结算：按 account + med_type 分组串行调用平台；自费项仅汇总。
     */
    OpInsurancePreSettleBatchResult preSettleBatch(OpInsurancePreSettleBatchCommand command);

    /** 单组 2207 正式结算（须在本地 {@code settleOutpatientPayment} 之前调用）。 */
    OpInsuranceConfirmResult confirmBeforeLocalSettle(OpInsuranceConfirmCommand command);

    /** 列出支付草稿下仍待确认的 ACTIVE 预结算批次。 */
    List<OpInsurancePreSettleResult> listActivePreSettlesByPaymentDraft(Long paymentDraftId);

    /** 列出支付单下未撤销的医保正式结算回执（混合结算可能多条）。 */
    List<OpInsuranceSettleRecordResult> listSettlementsByPayment(Long paymentId);

    /** 2208 撤销门诊正式结算。 */
    OpInsuranceRevokeSettleResult revokeSettle(OpInsuranceRevokeSettleCommand command);

    /** 2205 撤销未结算费用明细（预结算回滚）。 */
    OpInsuranceRevokeFeeDetailResult revokeFeeDetails(OpInsuranceRevokeFeeDetailCommand command);
}
