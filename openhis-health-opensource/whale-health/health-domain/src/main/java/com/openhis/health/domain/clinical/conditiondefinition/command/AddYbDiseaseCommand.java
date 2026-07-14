/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 新增医保诊断目录命令。
 */
@Getter
@Setter
public class AddYbDiseaseCommand {
    /** source枚举编码。 */
    private String sourceEnum;
    /** 病情编码。 */
    private String conditionCode;
    /** 名称。 */
    private String name;
    /** 医保标志。 */
    private Boolean ybFlag;
    /** 医保号。 */
    private String ybNo;
    /** 医保匹配标志。 */
    private Boolean ybMatchFlag;
    /** 疾病编码。 */
    private String diseaseCode;
    /** 状态枚举编码。 */
    private String statusEnum;
}
