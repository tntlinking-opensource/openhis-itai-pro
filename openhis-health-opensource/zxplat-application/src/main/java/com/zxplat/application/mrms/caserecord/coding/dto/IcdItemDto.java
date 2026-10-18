/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.coding.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

/**
 * ICD 编码候选项 DTO（诊断 ICD-10 / 手术国临 PD 共用；手术项可带 ybNo 提示未对码）。
 */
@Getter
@Setter
public class IcdItemDto {

    /** 目录定义主键（手术 PD id；诊断可空）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 编码（诊断国临/ICD；手术国临 procedureCode）。 */
    private String code;

    /** 名称。 */
    private String name;

    /** 拼音助记码。 */
    private String pyStr;

    /**
     * 医保对照码（手术 PD.yb_no；可空表示未对码，上传时阻断）。
     *
     * <p>诊断检索本期可不填；手术选码供前端提示。</p>
     */
    private String ybNo;
}
