/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.observationdefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存临床观察定义命令（新建 id 为空；更新传 id）。
 */
@Getter
@Setter
public class SaveObservationDefinitionCommand {

    /** 更新时主键；新建为空。 */
    private Long id;

    /** 租户标识（必填，唯一键维度）。 */
    private Long tenantId;

    /** 机构标识。 */
    private Long orgId;

    /** 院内编码。 */
    private String code;

    /** 展示名称。 */
    private String display;

    /** 值类型。 */
    private String valueTypeEnum;

    /** 单位编码。 */
    private String unitCode;

    /** 分类编码。 */
    private String categoryCode;

    /** 状态；空则默认 active。 */
    private String statusEnum;
}
