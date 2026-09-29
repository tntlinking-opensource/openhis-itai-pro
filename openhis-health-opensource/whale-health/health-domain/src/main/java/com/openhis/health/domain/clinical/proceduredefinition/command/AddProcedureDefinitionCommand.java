/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.command;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddProcedureDefinitionCommand {
    /** 目录来源码（clinical_proc_v3 / general_op）。 */
    private String sourceEnum;
    /** 目录主编码；空则领域生成业务序列号。 */
    private String procedureCode;
    /** 手术/操作名称。 */
    private String name;
    /** 拼音检索码；空则按名称重算。 */
    private String pyStr;
    /** 五笔检索码；空则按名称重算。 */
    private String wbStr;
    /** 类别/类型编码。 */
    private String typeCode;
    /** 描述。 */
    private String description;
    /** 发布状态（PublicationStatus code）。 */
    private String statusEnum;
    /** 医保标记。 */
    private Boolean ybFlag;
    /** 医保手术码。 */
    private String ybNo;
    /** 医保对码标记。 */
    private Boolean ybMatchFlag;
    /** 国家临床版手术编码。 */
    private String clinicCode;
    /**
     * 挂靠诊疗项目定义主键（国临行计费映射；可空）。
     */
    private Long activityDefinitionId;
}
