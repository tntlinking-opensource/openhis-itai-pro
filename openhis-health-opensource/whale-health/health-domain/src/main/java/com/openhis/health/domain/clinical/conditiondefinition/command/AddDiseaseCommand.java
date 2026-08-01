/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 新增诊断目录命令。
 */
@Getter
@Setter
public class AddDiseaseCommand {
    /** source枚举编码。 */
    private String sourceEnum;
    /** 病情编码。 */
    private String conditionCode;
    /** 名称。 */
    private String name;
    /** pystr。 */
    private String pyStr;
    /** wbstr。 */
    private String wbStr;
    /** 类型编码。 */
    private String typeCode;
    /** description。 */
    private String description;
    /** 状态枚举编码。 */
    private String statusEnum;

    /** 是否纳入医保目录（主数据维护页勾选）。 */
    private Boolean ybFlag;
    /** 医保目录编号或对照码。 */
    private String ybNo;
    /** 是否已完成医保编码匹配（对照状态，非业务准入）。 */
    private Boolean ybMatchFlag;
    /** 国家临床版诊断编码（国临码）。 */
    private String clinicCode;
}
