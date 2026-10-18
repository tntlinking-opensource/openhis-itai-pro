/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.settlement.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.exception.BusinessException;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 2204/2301 退单行 {@code init_feedetl_sn} 解析。
 *
 * <p>仅<strong>金额为负</strong>的冲销行写原费用流水号。住院反结再生行也会写 {@code refund_id}
 * 链回源 SETTLED，但金额仍为正、且源行已在 2302 撤销；若仍传 {@code init_feedetl_sn}
 * 平台会按退单查删已撤流水（HospFeed0801）。套餐子项流水为 {@code 父流水.序号} 时原流水同步替换父段。</p>
 */
public final class ChsFeeRefundInitFeedetlSupport {

    private ChsFeeRefundInitFeedetlSupport() {
    }

    /**
     * 按本批<strong>负向冲销行</strong>的 {@code refund_id} 一次装载冲销源收费项。
     *
     * <p>反结再生正数行虽带 {@code refund_id}，不参与装载（不写 init）。</p>
     *
     * @param chargeItemQueryService 收费项查询
     * @param chargeItems            本批待上传收费项（可含正数与冲销）
     * @return 源收费项 id → 实体；无冲销时 empty
     */
    public static Map<Long, ChargeItem> loadRefundSources(
            ChargeItemQueryService chargeItemQueryService,
            Collection<ChargeItem> chargeItems) {
        Map<Long, ChargeItem> empty = Map.of();
        // Gate：无批或无查询服务则无法解析原流水
        if (chargeItemQueryService == null || chargeItems == null || chargeItems.isEmpty()) {
            return empty;
        }
        LinkedHashSet<Long> sourceIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            // 仅金额为负且链回源行的冲销行需要 init；反结再生正数行跳过
            if (!isNegativeOffsetRefundLine(item)) {
                continue;
            }
            sourceIds.add(item.getRefundId());
        }
        // Pre：本批无退单行
        if (sourceIds.isEmpty()) {
            return empty;
        }
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(List.copyOf(sourceIds));
        List<ChargeItem> sources = chargeItemQueryService.loadChargeItemsByIds(query);
        Map<Long, ChargeItem> byId = new HashMap<>();
        if (sources != null) {
            for (ChargeItem source : sources) {
                // 跳过空行
                if (source == null || source.getId() == null) {
                    continue;
                }
                byId.put(source.getId(), source);
            }
        }
        // Gate：refund_id 须全部命中，否则退单无法组 init_feedetl_sn
        if (byId.size() != sourceIds.size()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND,
                "退费明细缺少原收费行，无法上传医保费用（init_feedetl_sn）");
        }
        return byId;
    }

    /**
     * 解析退单行 {@code init_feedetl_sn}；非负向冲销行返回 null。
     *
     * @param refundItem       收费明细（仅金额为负且含 refund_id 时写 init）
     * @param refundFeedetlSn  本行上传流水（父或 父.序号）
     * @param refundSourceById 已装载的源收费项
     * @return 原费用流水号；非冲销 / 反结再生正数为 null
     */
    public static String resolveInitFeedetlSn(
            ChargeItem refundItem,
            String refundFeedetlSn,
            Map<Long, ChargeItem> refundSourceById) {
        // Gate：非负向冲销（含反结再生正数行）不传 init，避免 HospFeed0801
        if (!isNegativeOffsetRefundLine(refundItem)) {
            return null;
        }
        // Gate：源 Map 缺失则无法组包
        if (refundSourceById == null || refundSourceById.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND,
                "退费明细未装载原收费行，无法上传医保费用（init_feedetl_sn）");
        }
        ChargeItem source = refundSourceById.get(refundItem.getRefundId());
        // Gate：refund_id 悬空
        if (source == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND,
                "退费明细原收费行不存在：" + refundItem.getRefundId());
        }
        String sourceParentSn = resolveParentFeedetlSn(source);
        String refundParentSn = resolveParentFeedetlSn(refundItem);
        // Pre：套餐子项流水=父.序号 → 原流水同步换父段
        if (isNotEmpty(refundFeedetlSn)
            && isNotEmpty(refundParentSn)
            && refundFeedetlSn.startsWith(refundParentSn + ".")) {
            String suffix = refundFeedetlSn.substring(refundParentSn.length());
            return sourceParentSn + suffix;
        }
        return sourceParentSn;
    }

    /**
     * 是否为须带 {@code init_feedetl_sn} 的平台退单行：金额为负且 {@code refund_id} 非空。
     *
     * <p>反结再生行金额为正，虽有 {@code refund_id} 也不算退单。</p>
     *
     * @param item 收费明细
     * @return true=负向冲销
     */
    private static boolean isNegativeOffsetRefundLine(ChargeItem item) {
        // Gate：无行或无链回则不是退单上传行
        if (item == null || item.getRefundId() == null) {
            return false;
        }
        // Gate：金额非负（含反结再生）不写 init
        return MonetaryAmountSupport.nullToZero(item.getTotalPrice()).signum() < 0;
    }

    /**
     * 父项费用流水号：优先 busNo，否则主键（与套餐展开同口径）。
     *
     * @param item 收费明细
     * @return 非空流水号
     */
    public static String resolveParentFeedetlSn(ChargeItem item) {
        // Gate：空项无法解析
        if (item == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费明细为空，无法解析 feedetl_sn");
        }
        if (isNotEmpty(item.getBusNo())) {
            return item.getBusNo().trim();
        }
        // Gate：无主键则无法回落
        if (item.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "收费明细缺少主键，无法解析 feedetl_sn");
        }
        return String.valueOf(item.getId());
    }

    /**
     * 数量为负时必须已解析出 init_feedetl_sn（本地总检，避免平台 HospFeeDtlBO_121）。
     *
     * @param cnt            上传数量
     * @param initFeedetlSn  原费用流水
     * @param feedetlSn      本行流水（报错提示）
     */
    public static void assertNegativeCntHasInit(
            java.math.BigDecimal cnt,
            String initFeedetlSn,
            String feedetlSn) {
        // Gate：非负数不校验
        if (cnt == null || cnt.signum() >= 0) {
            return;
        }
        // Gate：退单缺原流水
        if (isEmpty(initFeedetlSn)) {
            String snHint = feedetlSn;
            if (isEmpty(snHint)) {
                snHint = "-";
            }
            throw new BusinessException(
                "INS-011",
                "退单费用须填写原费用流水号(init_feedetl_sn)：feedetl_sn=" + snHint);
        }
    }
}
