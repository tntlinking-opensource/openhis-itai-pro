/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.facade;

import com.openhis.chs.domain.outpatient.dto.OpPlatformRegistrationResult;
import com.openhis.chs.domain.outpatient.dto.OpConfirmSettleCommand;
import com.openhis.chs.domain.outpatient.dto.OpConfirmSettleResult;
import com.openhis.chs.domain.outpatient.dto.OpPreSettleResult;
import com.openhis.chs.domain.outpatient.dto.OpRevokePlatformRegistrationCommand;
import com.openhis.chs.domain.outpatient.dto.OpRevokePlatformRegistrationResult;
import com.openhis.chs.domain.outpatient.dto.OpRevokeFeeDetailCommand;
import com.openhis.chs.domain.outpatient.dto.OpRevokeFeeDetailResult;
import com.openhis.chs.domain.outpatient.dto.OpRevokeSettleCommand;
import com.openhis.chs.domain.outpatient.dto.OpRevokeSettleResult;
import com.openhis.chs.domain.outpatient.dto.OpSettleContext;
import com.openhis.chs.domain.outpatient.dto.YbPersonInfoResult;
import com.openhis.chs.domain.outpatient.dto.YbPersonQueryCommand;
import com.openhis.chs.domain.outpatient.dto.YbTreatmentCheckCommand;
import com.openhis.chs.domain.outpatient.dto.YbTreatmentCheckResult;
import com.openhis.chs.domain.settlement.dto.SettlementCorrectionCommand;

/**
 * 门诊医保结算平台编排（2201→2203→2204→2206→2207）。
 */
public interface ChsOutpatientSettleOperations {

    /** 2201 确保平台挂号登记（已有 med_type 映射时跳过 2201）。 */
    OpPlatformRegistrationResult ensurePlatformRegistration(OpSettleContext context);

    /** 2203→2204→2206 同步预结算。 */
    OpPreSettleResult preSettle(OpSettleContext context);

    /** 2207 正式结算（须先有预结算快照）。 */
    OpConfirmSettleResult confirmSettle(OpConfirmSettleCommand command);

    /** 2208 撤销门诊正式结算。 */
    OpRevokeSettleResult revokeSettle(OpRevokeSettleCommand command);

    /** 2601 冲正原交易（超时/不确定态补偿）。 */
    void correctTransaction(SettlementCorrectionCommand command);

    /** 2205 撤销未结算费用明细（预结算回滚）。 */
    OpRevokeFeeDetailResult revokeFeeDetails(OpRevokeFeeDetailCommand command);

    /** 2202 撤销门诊平台挂号登记。 */
    OpRevokePlatformRegistrationResult revokePlatformRegistration(OpRevokePlatformRegistrationCommand command);

    /** 1101 读卡/人员信息（含当日缓存）。 */
    YbPersonInfoResult getPersonInfo(YbPersonQueryCommand command);

    /** 2001 人员待遇享受检查。 */
    YbTreatmentCheckResult checkPersonTreatment(YbTreatmentCheckCommand command);
}
