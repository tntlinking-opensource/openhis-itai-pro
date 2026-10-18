/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.proceduredefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 国临手术检索项（医生站选品 / DM-CLN-009 §4.6.3）。
 */
@Getter
@Setter
public class ProcedureDefinitionSearchItemQueryModel {
    /** 主键。 */
    private Long id;
    /** 国临主编码。 */
    private String procedureCode;
    /** 名称。 */
    private String name;
    /** 拼音检索码。 */
    private String pyStr;
    /** 五笔检索码。 */
    private String wbStr;
    /** 发布状态码（检索默认仅 ACTIVE）。 */
    private String statusEnum;
    /** 医保手术码（对码后；可空，上传侧另门控）。 */
    private String ybNo;
    /**
     * 挂靠诊疗项目定义主键；医生站选品落行须非空（AS-IP-DOR-012 BR-IP-SUR-004）。
     */
    private Long activityDefinitionId;
}
