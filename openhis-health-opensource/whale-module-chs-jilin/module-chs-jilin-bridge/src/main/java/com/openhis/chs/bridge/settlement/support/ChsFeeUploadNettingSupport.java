/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.settlement.support;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.inpatient.command.IpSettleChargeLine;
import com.openhis.chs.domain.outpatient.command.OpSettleChargeLine;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2204/2301 待上传行净量合并。
 *
 * <p>院内划价冲销在原行未上平台时，正负成对同批上传会触发长春
 * 「未查询到原费用流水号」。此时不传退单 {@code init_feedetl_sn}，只上传轧差后净量；
 * 被轧差吃掉的冲销行（及净量为 0 的原行）须本地记已上传，避免下次再当退单出站。
 * 原行已在平台落库的退单仍保留负数行 + {@code init_feedetl_sn}。</p>
 */
public final class ChsFeeUploadNettingSupport {

    private ChsFeeUploadNettingSupport() {
    }

    /**
     * 住院待上传净量计划。
     *
     * @param uploadLines        真正出站 2301 的行
     * @param locallyClosedLines 仅本地记已上传（轧差吃掉的冲销/净零原行）
     */
    public record IpNetUploadPlan(
        List<IpSettleChargeLine> uploadLines,
        List<IpSettleChargeLine> locallyClosedLines) {
    }

    /**
     * 门诊待上传净量计划。
     *
     * @param uploadLines        真正出站 2204 的行
     * @param locallyClosedLines 仅本地记已上传
     */
    public record OpNetUploadPlan(
        List<OpSettleChargeLine> uploadLines,
        List<OpSettleChargeLine> locallyClosedLines) {
    }

    /**
     * 住院待上传行：未上平台的冲销对轧差为净量。
     *
     * @param pending 尚未成功上传的 2301 行
     * @return 出站行 + 本地关闭行
     */
    public static IpNetUploadPlan planIpPendingUploadLines(List<IpSettleChargeLine> pending) {
        // Gate：空批
        if (pending == null || pending.isEmpty()) {
            return new IpNetUploadPlan(List.of(), List.of());
        }
        Map<String, IpSettleChargeLine> byFeedetlSn = indexIpByFeedetlSn(pending);
        Map<String, List<IpSettleChargeLine>> refundsByInit = groupIpRefundsByInit(pending);
        Set<String> consumedFeedetlSn = new HashSet<>();
        List<IpSettleChargeLine> uploadLines = new ArrayList<>();
        List<IpSettleChargeLine> locallyClosedLines = new ArrayList<>();

        // Pre：原行仍在本批 → 轧差净量
        for (Map.Entry<String, List<IpSettleChargeLine>> entry : refundsByInit.entrySet()) {
            String initSn = entry.getKey();
            List<IpSettleChargeLine> refunds = entry.getValue();
            IpSettleChargeLine original = byFeedetlSn.get(initSn);
            // 原行不在本批：退单稍后原样出站（平台退单）
            if (original == null) {
                continue;
            }
            BigDecimal netCnt = MonetaryAmountSupport.nullToZero(original.getCnt());
            BigDecimal netAmt = MonetaryAmountSupport.nullToZero(original.getDetItemFeeSumamt());
            for (IpSettleChargeLine refund : refunds) {
                netCnt = netCnt.add(MonetaryAmountSupport.nullToZero(refund.getCnt()));
                netAmt = netAmt.add(MonetaryAmountSupport.nullToZero(refund.getDetItemFeeSumamt()));
                markConsumed(consumedFeedetlSn, refund.getFeedetlSn());
                locallyClosedLines.add(refund);
            }
            markConsumed(consumedFeedetlSn, original.getFeedetlSn());
            // 净量为 0：正负完全对冲，原行也只本地关闭
            if (netCnt.signum() == 0) {
                locallyClosedLines.add(original);
                continue;
            }
            uploadLines.add(copyIpWithNet(original, netCnt, netAmt));
        }

        // Asm：未参与轧差的行按原序出站
        for (IpSettleChargeLine line : pending) {
            if (line == null || isEmpty(line.getFeedetlSn())) {
                continue;
            }
            if (consumedFeedetlSn.contains(line.getFeedetlSn().trim())) {
                continue;
            }
            uploadLines.add(line);
        }
        return new IpNetUploadPlan(uploadLines, locallyClosedLines);
    }

    /**
     * 门诊待上传行：与住院同口径净量合并。
     *
     * @param pending 尚未成功上传的 2204 行
     * @return 出站行 + 本地关闭行
     */
    public static OpNetUploadPlan planOpPendingUploadLines(List<OpSettleChargeLine> pending) {
        // Gate：空批
        if (pending == null || pending.isEmpty()) {
            return new OpNetUploadPlan(List.of(), List.of());
        }
        Map<String, OpSettleChargeLine> byFeedetlSn = indexOpByFeedetlSn(pending);
        Map<String, List<OpSettleChargeLine>> refundsByInit = groupOpRefundsByInit(pending);
        Set<String> consumedFeedetlSn = new HashSet<>();
        List<OpSettleChargeLine> uploadLines = new ArrayList<>();
        List<OpSettleChargeLine> locallyClosedLines = new ArrayList<>();

        for (Map.Entry<String, List<OpSettleChargeLine>> entry : refundsByInit.entrySet()) {
            String initSn = entry.getKey();
            List<OpSettleChargeLine> refunds = entry.getValue();
            OpSettleChargeLine original = byFeedetlSn.get(initSn);
            if (original == null) {
                continue;
            }
            BigDecimal netCnt = MonetaryAmountSupport.nullToZero(original.getCnt());
            BigDecimal netAmt = MonetaryAmountSupport.nullToZero(original.getDetItemFeeSumamt());
            for (OpSettleChargeLine refund : refunds) {
                netCnt = netCnt.add(MonetaryAmountSupport.nullToZero(refund.getCnt()));
                netAmt = netAmt.add(MonetaryAmountSupport.nullToZero(refund.getDetItemFeeSumamt()));
                markConsumed(consumedFeedetlSn, refund.getFeedetlSn());
                locallyClosedLines.add(refund);
            }
            markConsumed(consumedFeedetlSn, original.getFeedetlSn());
            if (netCnt.signum() == 0) {
                locallyClosedLines.add(original);
                continue;
            }
            uploadLines.add(copyOpWithNet(original, netCnt, netAmt));
        }

        for (OpSettleChargeLine line : pending) {
            if (line == null || isEmpty(line.getFeedetlSn())) {
                continue;
            }
            if (consumedFeedetlSn.contains(line.getFeedetlSn().trim())) {
                continue;
            }
            uploadLines.add(line);
        }
        return new OpNetUploadPlan(uploadLines, locallyClosedLines);
    }

    /**
     * 住院行按 feedetl_sn 建索引。
     *
     * @param pending 待上传行
     * @return feedetl_sn → 行
     */
    private static Map<String, IpSettleChargeLine> indexIpByFeedetlSn(List<IpSettleChargeLine> pending) {
        Map<String, IpSettleChargeLine> map = new HashMap<>();
        for (IpSettleChargeLine line : pending) {
            if (line == null || isEmpty(line.getFeedetlSn())) {
                continue;
            }
            map.put(line.getFeedetlSn().trim(), line);
        }
        return map;
    }

    /**
     * 住院冲销行按 init_feedetl_sn 分组。
     *
     * @param pending 待上传行
     * @return init_feedetl_sn → 冲销行列表
     */
    private static Map<String, List<IpSettleChargeLine>> groupIpRefundsByInit(List<IpSettleChargeLine> pending) {
        Map<String, List<IpSettleChargeLine>> map = new LinkedHashMap<>();
        for (IpSettleChargeLine line : pending) {
            if (line == null || isEmpty(line.getInitFeedetlSn())) {
                continue;
            }
            String initSn = line.getInitFeedetlSn().trim();
            List<IpSettleChargeLine> list = map.get(initSn);
            if (list == null) {
                list = new ArrayList<>();
                map.put(initSn, list);
            }
            list.add(line);
        }
        return map;
    }

    /**
     * 门诊行按 feedetl_sn 建索引。
     *
     * @param pending 待上传行
     * @return feedetl_sn → 行
     */
    private static Map<String, OpSettleChargeLine> indexOpByFeedetlSn(List<OpSettleChargeLine> pending) {
        Map<String, OpSettleChargeLine> map = new HashMap<>();
        for (OpSettleChargeLine line : pending) {
            if (line == null || isEmpty(line.getFeedetlSn())) {
                continue;
            }
            map.put(line.getFeedetlSn().trim(), line);
        }
        return map;
    }

    /**
     * 门诊冲销行按 init_feedetl_sn 分组。
     *
     * @param pending 待上传行
     * @return init_feedetl_sn → 冲销行列表
     */
    private static Map<String, List<OpSettleChargeLine>> groupOpRefundsByInit(List<OpSettleChargeLine> pending) {
        Map<String, List<OpSettleChargeLine>> map = new LinkedHashMap<>();
        for (OpSettleChargeLine line : pending) {
            if (line == null || isEmpty(line.getInitFeedetlSn())) {
                continue;
            }
            String initSn = line.getInitFeedetlSn().trim();
            List<OpSettleChargeLine> list = map.get(initSn);
            if (list == null) {
                list = new ArrayList<>();
                map.put(initSn, list);
            }
            list.add(line);
        }
        return map;
    }

    /**
     * 标记已轧差消费的流水号。
     *
     * @param consumed  消费集合
     * @param feedetlSn 流水号
     */
    private static void markConsumed(Set<String> consumed, String feedetlSn) {
        if (isNotEmpty(feedetlSn)) {
            consumed.add(feedetlSn.trim());
        }
    }

    /**
     * 复制正数行并写入轧差后的数量/金额，清除 init。
     *
     * @param original 原正数行
     * @param netCnt   净数量
     * @param netAmt   净金额
     * @return 新行
     */
    private static IpSettleChargeLine copyIpWithNet(
            IpSettleChargeLine original,
            BigDecimal netCnt,
            BigDecimal netAmt) {
        IpSettleChargeLine copy = new IpSettleChargeLine();
        copy.setChargeItemId(original.getChargeItemId());
        copy.setFeedetlSn(original.getFeedetlSn());
        copy.setInitFeedetlSn(null);
        copy.setMedListCodg(original.getMedListCodg());
        copy.setMedinsListCodg(original.getMedinsListCodg());
        copy.setDetItemFeeSumamt(MonetaryAmountSupport.scaleCalc(netAmt));
        copy.setCnt(netCnt);
        copy.setPric(original.getPric());
        copy.setFeeOcurTime(original.getFeeOcurTime());
        copy.setDrordNo(original.getDrordNo());
        copy.setBilgDeptCodg(original.getBilgDeptCodg());
        copy.setBilgDeptName(original.getBilgDeptName());
        copy.setBilgDrCodg(original.getBilgDrCodg());
        copy.setBilgDrName(original.getBilgDrName());
        return copy;
    }

    /**
     * 复制门诊正数行并写入轧差后的数量/金额。
     *
     * @param original 原正数行
     * @param netCnt   净数量
     * @param netAmt   净金额
     * @return 新行
     */
    private static OpSettleChargeLine copyOpWithNet(
            OpSettleChargeLine original,
            BigDecimal netCnt,
            BigDecimal netAmt) {
        OpSettleChargeLine copy = new OpSettleChargeLine();
        copy.setChargeItemId(original.getChargeItemId());
        copy.setFeedetlSn(original.getFeedetlSn());
        copy.setInitFeedetlSn(null);
        copy.setMedListCodg(original.getMedListCodg());
        copy.setMedinsListCodg(original.getMedinsListCodg());
        copy.setDetItemFeeSumamt(MonetaryAmountSupport.scaleCalc(netAmt));
        copy.setCnt(netCnt);
        copy.setPric(original.getPric());
        copy.setFeeOcurTime(original.getFeeOcurTime());
        copy.setRxno(original.getRxno());
        copy.setDiseCodg(original.getDiseCodg());
        copy.setBilgDeptCodg(original.getBilgDeptCodg());
        copy.setBilgDeptName(original.getBilgDeptName());
        copy.setBilgDrCodg(original.getBilgDrCodg());
        copy.setBilgDrName(original.getBilgDrName());
        copy.setItemName(original.getItemName());
        copy.setHilistType(original.getHilistType());
        copy.setChrgType(original.getChrgType());
        copy.setHilistLv(original.getHilistLv());
        copy.setSpec(original.getSpec());
        copy.setSpecUnt(original.getSpecUnt());
        return copy;
    }
}
