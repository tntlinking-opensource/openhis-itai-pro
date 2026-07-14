/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 编辑诊断目录命令。
 */
@Getter
@Setter
public class EditDiseaseCommand {
    /** 主键。 */
    private Long id;
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
    /** 医保标志。 */
    private Boolean ybFlag;
    /** 医保号。 */
    private String ybNo;

    /** 国家/地方疾病编码（医保对照用）。 */
    private String diseaseCode;
    /** 医保匹配标志。 */
    private Boolean ybMatchFlag;
    /** 状态枚举编码（启用/停用）。 */
    private String statusEnum;
}
