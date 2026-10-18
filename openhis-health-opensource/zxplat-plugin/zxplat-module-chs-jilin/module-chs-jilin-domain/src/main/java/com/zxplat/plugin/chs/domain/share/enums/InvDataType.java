/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.enums;

/**
 * 进销存【3507】删除接口入参 {@code inv_data_type}（要删哪类进销存数据）。
 *
 * <p>与 {@link InvChgType} 不同：3507 按数据大类删（盘存/变更/采购/销售），不是 101～111 细类。</p>
 */
public enum InvDataType implements ChsCodeEnum {

    /** 3501 盘存数据（code 1） */
    STOCKTAKE("1", "盘存"),
    /** 3502 库存变更数据（code 2） */
    INV_CHANGE("2", "库存变更"),
    /** 3503 采购数据（code 3） */
    PURCHASE("3", "采购"),
    /** 3505 销售数据（code 4） */
    SALE("4", "销售");

    private final String code;
    private final String label;

    InvDataType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
