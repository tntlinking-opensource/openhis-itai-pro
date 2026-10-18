/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.model;

import lombok.Data;

/**
 * 院内产品定义 chrgitm_lv 变更痕迹。
 */
@Data
public class HisProductLvChange {

    /** 目标表名（如 med_medication_definition）。 */
    private String targetTable;

    /** 目标行主键。 */
    private Long targetId;

    /** 变更前等级。 */
    private String beforeLv;

    /** 变更后等级。 */
    private String afterLv;
}
