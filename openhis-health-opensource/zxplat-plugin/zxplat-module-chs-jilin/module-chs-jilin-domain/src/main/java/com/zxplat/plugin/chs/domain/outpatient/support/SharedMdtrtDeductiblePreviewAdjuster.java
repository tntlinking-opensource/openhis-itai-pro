/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.support;

import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.plugin.chs.domain.outpatient.result.OpPreSettleResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同一平台就诊（{@code mdtrt_id}）多组 2206 的起付线预览收口。
 *
 * <p>每组 2206 都按「本组将扣起付线」报价，正式 2207 按组顺序只扣一次。
 * 本类只改内存预览（起付线、统筹、基金、个账、个人现金），不改已落库的 2206 快照。</p>
 */
public final class SharedMdtrtDeductiblePreviewAdjuster {

    /** 金额小数位，与平台 setlinfo 两位一致。 */
    private static final int MONEY_SCALE = 2;

    /** 工具类，禁止实例化。 */
    private SharedMdtrtDeductiblePreviewAdjuster() {
    }

    /**
     * 按列表顺序收口同一 {@code mdtrt_id} 的起付线预览。
     *
     * <p>顺序须与随后 2207 的批次顺序一致。只出现一次的就诊、或缺少合法统筹比例的组保持平台原报价。</p>
     *
     * @param groups 同一笔批量预结算的各组结果；允许 null
     */
    public static void adjust(List<? extends OpPreSettleResult> groups) {
        if (groups == null || groups.size() < 2) {
            return;
        }
        Map<String, Integer> countByMdtrt = new LinkedHashMap<>();
        Map<String, BigDecimal> remainingByMdtrt = new LinkedHashMap<>();
        for (OpPreSettleResult group : groups) {
            String mdtrtId = resolveMdtrtId(group);
            // 无平台就诊号的组无法判断是否共用起付线，不参与收口
            if (mdtrtId == null) {
                continue;
            }
            Integer count = countByMdtrt.get(mdtrtId);
            if (count == null) {
                countByMdtrt.put(mdtrtId, 1);
            } else {
                countByMdtrt.put(mdtrtId, count + 1);
            }
            BigDecimal quoted = zeroIfNull(group.getActPayDedc());
            BigDecimal remaining = remainingByMdtrt.get(mdtrtId);
            // 各组 2206 看到的是同一剩余额度，取最大报价作为本批开始前的剩余起付线
            if (remaining == null || quoted.compareTo(remaining) > 0) {
                remainingByMdtrt.put(mdtrtId, quoted);
            }
        }
        for (OpPreSettleResult group : groups) {
            String mdtrtId = resolveMdtrtId(group);
            if (mdtrtId == null) {
                continue;
            }
            Integer count = countByMdtrt.get(mdtrtId);
            // 本就诊只有一组时，2206 报价就是 2207 会扣的起付线
            if (count == null || count < 2) {
                continue;
            }
            applySharedDeductible(group, mdtrtId, remainingByMdtrt);
        }
    }

    /**
     * 把本组报价起付线收成「本批剩余额度」以内，并把少扣部分按统筹比例从现金挪到基金。
     *
     * @param group             当前组预览
     * @param mdtrtId           平台就诊号
     * @param remainingByMdtrt  各就诊尚未被前序组占用的起付线
     */
    private static void applySharedDeductible(
        OpPreSettleResult group,
        String mdtrtId,
        Map<String, BigDecimal> remainingByMdtrt) {
        BigDecimal remaining = remainingByMdtrt.get(mdtrtId);
        if (remaining == null) {
            remaining = BigDecimal.ZERO;
        }
        BigDecimal quoted = zeroIfNull(group.getActPayDedc());
        BigDecimal actual = quoted;
        // 前序组已经占用的额度，本组正式结算不会再扣
        if (quoted.compareTo(remaining) > 0) {
            actual = remaining;
        }
        BigDecimal released = quoted.subtract(actual);
        BigDecimal nextRemaining = remaining.subtract(actual);
        if (nextRemaining.signum() < 0) {
            nextRemaining = BigDecimal.ZERO;
        }
        // 本组仍按报价扣起付线，只减少本就诊剩余额度
        if (released.signum() == 0) {
            remainingByMdtrt.put(mdtrtId, nextRemaining);
            return;
        }
        BigDecimal rate = group.getPoolPropSelfpay();
        // 比例缺失或越界时无法可靠改写基金，保持平台原报价，也不提前扣减剩余额度
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            return;
        }
        BigDecimal delta = released.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal fund = zeroIfNull(group.getFundPaySumamt()).add(delta);
        BigDecimal cash = zeroIfNull(group.getPsnCashPay()).subtract(delta);
        BigDecimal acct = zeroIfNull(group.getAcctPay());
        // 现金不够承接少扣的起付线时，差额改由个账承担（个账优先支付个人负担）
        if (cash.signum() < 0) {
            acct = acct.add(cash);
            cash = BigDecimal.ZERO;
            // 个账也不够时放弃改写，避免预览出现负金额
            if (acct.signum() < 0) {
                return;
            }
        }
        group.setFundPaySumamt(fund.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        // 起付线释放只增加基本统筹，其它基金分项保持原报价
        if (group.getHifpPay() != null) {
            group.setHifpPay(group.getHifpPay().add(delta).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }
        group.setPsnCashPay(cash.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        group.setAcctPay(acct.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        group.setActPayDedc(actual.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        remainingByMdtrt.put(mdtrtId, nextRemaining);
    }

    /**
     * 取可用于分组的平台就诊号。
     *
     * @param group 预结算结果
     * @return 去空白后的 mdtrt_id；空则 null
     */
    private static String resolveMdtrtId(OpPreSettleResult group) {
        if (group == null || StringUtils.isBlank(group.getMdtrtId())) {
            return null;
        }
        return group.getMdtrtId().trim();
    }

    /**
     * 空金额按 0 参与收口，避免漏加起付线。
     *
     * @param amount 平台金额
     * @return 非空金额
     */
    private static BigDecimal zeroIfNull(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount;
    }
}
