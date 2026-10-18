/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.specimentubemerge.command;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * 合管绑定对齐行命令。
 */
@Getter
@Setter
public class AlignSpecimenTubeMergeLineCommand {

    /**
     * 设备内合管组序号（从 1 起）。
     *
     * <p>同组行共用耗材/采血费并合管；不同组不合管。</p>
     */
    private Integer groupNo;

    /** 目标表名（wkf_activity_definition / adm_device_definition）。 */
    private String targetTable;

    /** 目标主键。 */
    private Long targetId;

    /** 耗材数量（activity 行须为空）。 */
    private BigDecimal quantity;

    /** 耗材单位 code（activity 行须为空）。 */
    private String unitCode;

    /** 展示排序。 */
    private Integer displayOrder;
}
