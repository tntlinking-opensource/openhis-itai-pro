/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.mapper;

import com.openhis.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.openhis.chs.gateway.scheme.common.model.ChsSetlinfoOutput;

/**
 * {@link ChsSetlinfoOutput} ↔ {@link PlatformSetlinfoFields} 字段映射（gateway Output → domain 模型）。
 *
 * <p>双向纯内存转换，无 IO。</p>
 */
public final class SetlinfoIntegrationMapper {

    private SetlinfoIntegrationMapper() {
    }

    public static PlatformSetlinfoFields toPlatformFields(ChsSetlinfoOutput source) {
        if (source == null) {
            return null;
        }
        PlatformSetlinfoFields target = new PlatformSetlinfoFields();
        target.setSetlId(source.getSetlId());
        target.setMdtrtId(source.getMdtrtId());
        target.setPsnNo(source.getPsnNo());
        target.setMedfeeSumamt(source.getMedfeeSumamt());
        target.setFundPaySumamt(source.getFundPaySumamt());
        target.setAcctPay(source.getAcctPay());
        target.setPsnCashPay(source.getPsnCashPay());
        target.setFulamtOwnpayAmt(source.getFulamtOwnpayAmt());
        target.setOverlmtSelfpay(source.getOverlmtSelfpay());
        target.setPreselfpayAmt(source.getPreselfpayAmt());
        target.setInscpScpAmt(source.getInscpScpAmt());
        target.setInsutype(source.getInsutype());
        target.setMedType(source.getMedType());
        target.setPsnName(source.getPsnName());
        target.setCvlservFlag(source.getCvlservFlag());
        target.setSetlTime(source.getSetlTime());
        target.setHifpPay(source.getHifpPay());
        target.setCvlservPay(source.getCvlservPay());
        target.setHifmiPay(source.getHifmiPay());
        target.setHifobPay(source.getHifobPay());
        target.setHifesPay(source.getHifesPay());
        target.setPoolPropSelfpay(source.getPoolPropSelfpay());
        target.setMafPay(source.getMafPay());
        target.setOthPay(source.getOthPay());
        target.setPsnPartAmt(source.getPsnPartAmt());
        target.setHospPartAmt(source.getHospPartAmt());
        target.setBalc(source.getBalc());
        target.setAcctMulaidPay(source.getAcctMulaidPay());
        target.setPsnType(source.getPsnType());
        target.setActPayDedc(source.getActPayDedc());
        target.setHifdmPay(source.getHifdmPay());
        target.setExpContent(source.getExpContent());
        target.setMedinsSetlId(source.getMedinsSetlId());
        target.setClrOptins(source.getClrOptins());
        target.setClrWay(source.getClrWay());
        target.setClrType(source.getClrType());
        return target;
    }

    public static ChsSetlinfoOutput fromPlatformFields(PlatformSetlinfoFields source) {
        if (source == null) {
            return null;
        }
        ChsSetlinfoOutput target = new ChsSetlinfoOutput();
        target.setSetlId(source.getSetlId());
        target.setMdtrtId(source.getMdtrtId());
        target.setPsnNo(source.getPsnNo());
        target.setMedfeeSumamt(source.getMedfeeSumamt());
        target.setFundPaySumamt(source.getFundPaySumamt());
        target.setAcctPay(source.getAcctPay());
        target.setPsnCashPay(source.getPsnCashPay());
        target.setFulamtOwnpayAmt(source.getFulamtOwnpayAmt());
        target.setOverlmtSelfpay(source.getOverlmtSelfpay());
        target.setPreselfpayAmt(source.getPreselfpayAmt());
        target.setInscpScpAmt(source.getInscpScpAmt());
        target.setInsutype(source.getInsutype());
        target.setMedType(source.getMedType());
        target.setPsnName(source.getPsnName());
        target.setCvlservFlag(source.getCvlservFlag());
        target.setSetlTime(source.getSetlTime());
        target.setHifpPay(source.getHifpPay());
        target.setCvlservPay(source.getCvlservPay());
        target.setHifmiPay(source.getHifmiPay());
        target.setHifobPay(source.getHifobPay());
        target.setHifesPay(source.getHifesPay());
        target.setPoolPropSelfpay(source.getPoolPropSelfpay());
        target.setMafPay(source.getMafPay());
        target.setOthPay(source.getOthPay());
        target.setPsnPartAmt(source.getPsnPartAmt());
        target.setHospPartAmt(source.getHospPartAmt());
        target.setBalc(source.getBalc());
        target.setAcctMulaidPay(source.getAcctMulaidPay());
        target.setPsnType(source.getPsnType());
        target.setActPayDedc(source.getActPayDedc());
        target.setHifdmPay(source.getHifdmPay());
        target.setExpContent(source.getExpContent());
        target.setMedinsSetlId(source.getMedinsSetlId());
        target.setClrOptins(source.getClrOptins());
        target.setClrWay(source.getClrWay());
        target.setClrType(source.getClrType());
        return target;
    }
}
