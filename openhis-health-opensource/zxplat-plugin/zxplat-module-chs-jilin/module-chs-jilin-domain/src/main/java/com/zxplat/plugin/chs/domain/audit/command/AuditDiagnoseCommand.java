/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 3101 诊断节点（fsi_diagnose_dtos 元素） */
@Data
@Accessors(chain = true)
public class AuditDiagnoseCommand {

    /** 诊断 ID */
    private String diseId;
    /** 出入诊断类别 */
    private String inoutDiseType;
    /** 主诊标志 */
    private String maindiseFlag;
    /** 诊断排序号 */
    private String diasSrtNo;
    /** 诊断编码 */
    private String diseCodg;
    /** 诊断名称 */
    private String diseName;
    /** 诊断时间 */
    private String diseDate;
}
