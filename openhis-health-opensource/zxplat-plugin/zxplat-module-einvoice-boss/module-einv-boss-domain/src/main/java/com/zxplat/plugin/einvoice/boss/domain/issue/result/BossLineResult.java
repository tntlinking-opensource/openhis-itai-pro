/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.issue.result;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 博思报文明细行读模型。
 */
@Data
public class BossLineResult {

    /** 明细主键。 */
    private Long lineId;

    /** 行号。 */
    private Integer lineNo;

    /** 博思收费项目编码。 */
    private String chargeCode;

    /** 博思收费项目名称（统计大类）。 */
    private String chargeName;

    /**
     * 清单具体项目名称（药品/诊疗名）；调额页「项目名称」优先展示本字段。
     */
    private String itemName;

    /** 清单医保分类展示（甲类/乙类/丙类/自费）。 */
    private String listRemark;

    /** 清单医保分类码（1甲/2乙/3丙）。 */
    private String listMedicalCareType;

    /** 计量单位。 */
    private String unit;

    /** 标准金额。 */
    private BigDecimal stdAmount;

    /** 实际开票金额（调额后以本字段为准）。 */
    private BigDecimal amount;

    /** 是否经人工调额。 */
    private boolean adjusted;
}
