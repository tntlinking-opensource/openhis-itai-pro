/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.model;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.zxplat.plugin.chs.domain.settlement.PreSettleSnapshot;

/**
 * 平台收费批次（ChargeBatch）组合键：门诊以 chrg_bchno 为真键，住院以 IP{preSettleId} 关联 */
public record ChargeBatch(
    ChrgBchno chrgBchno,
    PlatformMdtrtId mdtrtId,
    Long preSettleId,
    Long paymentDraftId,
    Long accountId) {

    public static ChargeBatch fromClinicPreSettle(YbClinicPreSettle pre) {
        if (pre == null || isEmpty(pre.getChrgBchno()) || isEmpty(pre.getMdtrtId())) {
            throw new IllegalArgumentException("预结算快照缺少 chrg_bchno 或 mdtrt_id");
        }
        return new ChargeBatch(
            ChrgBchno.of(pre.getChrgBchno()),
            PlatformMdtrtId.of(pre.getMdtrtId()),
            pre.getId(),
            pre.getPaymentDraftId(),
            pre.getAccountId());
    }

    public static ChargeBatch fromInpatientPreSettle(YbInpatientPreSettle pre) {
        if (pre == null) {
            throw new IllegalArgumentException("预结算快照不能为空");
        }
        return fromPreSettle(pre, pre.getPaymentDraftId(), null);
    }

    public static ChargeBatch fromPreSettle(PreSettleSnapshot pre, Long paymentDraftId, Long accountId) {
        if (pre == null || isEmpty(pre.resolveBatchKey()) || isEmpty(pre.getMdtrtId())) {
            throw new IllegalArgumentException("预结算快照缺少批次键或 mdtrt_id");
        }
        return new ChargeBatch(
            ChrgBchno.of(pre.resolveBatchKey()),
            PlatformMdtrtId.of(pre.getMdtrtId()),
            pre.getId(),
            paymentDraftId,
            accountId);
    }

    /** 返回平台 chrg_bchno 字符串；住院场景为 {@code IP{preSettleId}} */
    public String batchKey() {
        if (chrgBchno != null) {
            return chrgBchno.value();
        }
        return null;
    }
}
