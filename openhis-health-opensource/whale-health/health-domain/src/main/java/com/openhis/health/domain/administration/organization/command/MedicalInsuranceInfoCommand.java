/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.organization.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 机构医保定点信息命令片段：随创建/修改机构一并提交。
 */
@Getter
@Setter
public class MedicalInsuranceInfoCommand {
    /** 医保机构编号。 */
    private String ybNo;
    /** 医保机构名称。 */
    private String ybName;
    /** 医药机构标识（国家医保编码体系）。 */
    private String medinsId;
    /** 医保区划。 */
    private String medinsAdmdvs;
    /** 医药机构类型。 */
    private String medinsType;
    /** 医药机构等级。 */
    private String medinsLv;
}
