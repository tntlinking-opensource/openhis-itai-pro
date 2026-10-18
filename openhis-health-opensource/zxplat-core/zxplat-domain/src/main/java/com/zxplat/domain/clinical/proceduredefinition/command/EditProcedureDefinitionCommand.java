/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.proceduredefinition.command;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditProcedureDefinitionCommand {
    /** 手术定义主键。 */
    private Long id;
    /** 目录来源码。 */
    private String sourceEnum;
    /** 目录主编码。 */
    private String procedureCode;
    /** 手术/操作名称。 */
    private String name;
    /** 拼音检索码。 */
    private String pyStr;
    /** 五笔检索码。 */
    private String wbStr;
    /** 类别/类型编码。 */
    private String typeCode;
    /** 描述。 */
    private String description;
    /** 医保标记。 */
    private Boolean ybFlag;
    /** 医保手术码。 */
    private String ybNo;
    /** 医保对码标记。 */
    private Boolean ybMatchFlag;
    /** 国家临床版手术编码。 */
    private String clinicCode;
    /** 发布状态；空表示保持原状态。 */
    private String statusEnum;
    /**
     * 挂靠诊疗项目定义主键；非空时改绑。
     *
     * <p>{@code null} 表示本轮不修改存量挂靠（维护端尚未传该字段时避免误清空）。</p>
     */
    private Long activityDefinitionId;
}
