/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 电子处方药品明细行（本页独立开方，不来自院内药嘱）。
 */
@Data
@Accessors(chain = true)
public class ElepRxDrugLine {

    /** 医保药品目录编码（1301 {@code medListCodg}）。 */
    private String medListCodg;

    /** 药品通用名。 */
    private String drugGenname;

    /** 药品名称。 */
    private String drugName;

    /** 规格。 */
    private String drugSpec;

    /** 剂型。 */
    private String drugDosform;

    /** 处方项目分类代码（11 西药 / 12 中成药）。 */
    private String rxItemTypeCode;

    /** 处方项目分类名称。 */
    private String rxItemTypeName;

    /** 总量。 */
    private String drugCnt;

    /** 总量单位。 */
    private String drugDosunt;

    /** 用药途径代码。 */
    private String medcWayCodg;

    /** 用药途径描述。 */
    private String medcWayDscr;

    /** 用药天数。 */
    private String medcDays;

    /** 单次剂量。 */
    private String sinDoscnt;

    /** 单次剂量单位。 */
    private String sinDosunt;

    /** 频次编码。 */
    private String usedFrquCodg;

    /** 频次名称。 */
    private String usedFrquName;

    /** 用药开始时间（3-03 {@code medcBegntime}，yyyy-MM-dd HH:mm:ss）。 */
    private String medcBegntime;

    /** 用药结束时间（3-03 {@code medcEndtime}，yyyy-MM-dd HH:mm:ss）。 */
    private String medcEndtime;

    /** 生产厂家（3-03 {@code prdrName}，可按通用名开方）。 */
    private String prdrName;

    /** 药品商品名（3-03 {@code drugProdname}，可空）。 */
    private String drugProdname;
}
