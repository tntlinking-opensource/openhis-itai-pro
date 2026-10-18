/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.catalog.model;

import lombok.Data;

/** 【1312】医保目录信息查询单行 output（平台分页 rows 元素） */
@Data
public class Cat1312RowOutput {

    private String hilistCode;
    private String hilistName;
    private String insuAdmdvs;
    private String begndate;
    private String enddate;
    private String chrgitmLv;
    private String lmtUsedFlag;
    private String listType;
    private String pinyin;
    private String wubi;
    private String valiFlag;
    private String updtTime;
}
