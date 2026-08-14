/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.settlement.support;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 2204/2301 退单行 {@code init_feedetl_sn} 解析。
 *
 * <p>冲销收费明细 {@code refund_id} 指向原正数行；退单上传须带原费用流水号，
 * 套餐子项流水为 {@code 父流水.序号} 时原流水同步替换父段。</p>
 */
public final class ChsFeeRefundInitFeedetlSupport {

    private ChsFeeRefundInitFeedetlSupport() {
    }

    /**
     * 按本批收费行的 {@code refund_id} 一次装载冲销源收费项。
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
            // 仅冲销行带 refund_id
            if (item == null || item.getRefundId() == null) {
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
     * 解析退单行 {@code init_feedetl_sn}；非冲销行返回 null。
     *
     * @param refundItem       冲销收费明细（须含 refund_id）
     * @param refundFeedetlSn  本行上传流水（父或 父.序号）
     * @param refundSourceById 已装载的源收费项
     * @return 原费用流水号；非冲销为 null
     */
    public static String resolveInitFeedetlSn(
            ChargeItem refundItem,
            String refundFeedetlSn,
            Map<Long, ChargeItem> refundSourceById) {
        // Gate：非冲销行不传 init
        if (refundItem == null || refundItem.getRefundId() == null) {
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
