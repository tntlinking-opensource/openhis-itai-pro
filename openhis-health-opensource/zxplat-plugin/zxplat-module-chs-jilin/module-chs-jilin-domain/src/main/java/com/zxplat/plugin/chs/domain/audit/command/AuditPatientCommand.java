/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 3101 患者节点（patient_dtos 元素） */
@Data
@Accessors(chain = true)
public class AuditPatientCommand {

    /** 参保人唯一 ID（联调常用 psn_no） */
    private String patnId;
    /** 姓名 */
    private String patnName;
    /** 性别码 */
    private String gend;
    /** 出生日期 yyyy-MM-dd */
    private String brdy;
    /** 统筹区 */
    private String poolarea;
    /** 当前就诊 ID */
    private String currMdtrtId;
    /** 就诊集合 */
    private List<AuditEncounterCommand> encounters = new ArrayList<>();
}
