/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.enums;

import java.math.BigDecimal;

/**
 * 国家医保进销存【3502】上传字段 {@code inv_chg_type}（表 311 库存变更类型）。
 *
 * <p><b>业务映射示例</b>：科室领用 → {@link #OTHER_OUT}；科室退领 → {@link #OTHER_IN}；
 * 盘点盈 → {@link #STOCKTAKE_PROFIT}；调拨出库 → {@link #TRANSFER_OUT}。</p>
 * <p>入库类编码要求盈亏数量为正，出库类为负，见 {@link #validateQtyDirection(BigDecimal)}。</p>
 */
public enum InvChgType implements ChsCodeEnum {

    /** 调拨入库（101） */
    TRANSFER_IN("101", "调拨入库", true),
    /** 调拨出库（102） */
    TRANSFER_OUT("102", "调拨出库", false),
    /** 盘盈（103） */
    STOCKTAKE_PROFIT("103", "盘盈", true),
    /** 盘损（104） */
    STOCKTAKE_LOSS("104", "盘损", false),
    /** 销毁（105） */
    DESTROY("105", "销毁", false),
    /** 其他入库（106；科室退领等） */
    OTHER_IN("106", "其他入库", true),
    /** 其他出库（107；科室领用等） */
    OTHER_OUT("107", "其他出库", false),
    /** 商品采购入库（108） */
    PURCHASE_IN("108", "商品采购入库", true),
    /** 商品退货出库（109） */
    PURCHASE_RETURN_OUT("109", "商品退货出库", false),
    /** 赠药入库（110） */
    GIFT_IN("110", "赠药入库", true),
    /** 赠药退回出库（111） */
    GIFT_RETURN_OUT("111", "赠药退回出库", false);

    private final String code;
    private final String label;
    private final boolean stockIncrease;

    InvChgType(String code, String label, boolean stockIncrease) {
        this.code = code;
        this.label = label;
        this.stockIncrease = stockIncrease;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }

    /**
     * 是否为库存增加方向（101/103/106 等入库类）。
     */
    public boolean isStockIncrease() {
        return stockIncrease;
    }

    /**
     * 是否为库存减少方向（102/104/107 等出库类）。
     */
    public boolean isStockDecrease() {
        return !stockIncrease;
    }

    /**
     * 判断字符串是否为平台接受的 inv_chg_type（101～111）。
     */
    public static boolean isValidCode(String code) {
        return fromCodeOptional(code) != null;
    }

    /**
     * 按编码解析；无效或空白返回 {@code null}。
     */
    public static InvChgType fromCodeOptional(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (InvChgType item : values()) {
            if (item.code.equals(trimmed)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 按编码解析；无效时抛 {@link IllegalArgumentException}。
     */
    public static InvChgType requireCode(String code) {
        InvChgType resolved = fromCodeOptional(code);
        if (resolved == null) {
            throw new IllegalArgumentException("无效的 inv_chg_type: " + code);
        }
        return resolved;
    }

    /**
     * 校验 inv_chg_type 与盈亏数量符号是否一致（入库为正、出库为负）。
     *
     * @param qty 3502 盈亏数量（有符号变化量）
     */
    public void validateQtyDirection(BigDecimal qty) {
        if (qty == null) {
            return;
        }
        // 入库类：cnt 须为正
        if (stockIncrease && qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("inv_chg_type=" + code + " 要求盈亏数量为正");
        }
        // 出库类：cnt 须为负
        if (!stockIncrease && qty.compareTo(BigDecimal.ZERO) >= 0) {
            throw new IllegalStateException("inv_chg_type=" + code + " 要求盈亏数量为负");
        }
    }

    /**
     * 将操作单位数量转为 3502 {@code cnt}：入库类为正、出库类为负。
     *
     * <p>内部<strong>先 abs 再按类型赋符号</strong>，不是对 HIS 原值再 {@code negate}。
     * 故页面报损/盘亏已填负数（如 -5）时：abs→5 → 出库类再赋负 → 仍为 -5，不会负负得正。</p>
     *
     * @param absoluteOperationQty 操作单位变化量（绝对值或带符号均可，内部统一 abs）
     * @return 有符号 cnt；入参 null 时 null
     */
    public BigDecimal toSignedCnt(BigDecimal absoluteOperationQty) {
        if (absoluteOperationQty == null) {
            return null;
        }
        // 先去掉 HIS/页面符号，再只按 inv_chg_type 方向赋正负（防负负得正）
        BigDecimal abs = absoluteOperationQty.abs();
        // 入库/盘盈等：正数
        if (stockIncrease) {
            return abs;
        }
        // 出库/盘损/销毁等：负数
        return abs.negate();
    }
}
