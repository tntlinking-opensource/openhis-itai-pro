/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.enums;

/**
 * 1312/3301 等目录接口字段 {@code list_type}（医保目录类别）。
 *
 * <p>决定查西药、饮片、诊疗还是耗材国家目录；与 {@link ChsInfNo} 13xx 下载交易对应。</p>
 */
public enum ListType implements ChsCodeEnum {

    /** 西药中成药（101；1301 下载） */
    WESTERN_DRUG("101", "西药中成药"),
    /** 中药饮片（102；1302 下载） */
    HERB("102", "中药饮片"),
    /** 医疗服务项目（201；1305 下载） */
    MEDICAL_SERVICE("201", "医疗服务项目"),
    /** 医用耗材（301；1306 下载） */
    MEDICAL_CONSUMABLE("301", "医用耗材");

    private final String code;
    private final String label;

    ListType(String code, String label) {
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
