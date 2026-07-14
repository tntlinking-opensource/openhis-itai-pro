/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.masterdata.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 主数据下拉选项（contracts 出站 DTO）。
 */
@Getter
@Setter
public class MasterDataOption {

    /** 排序号。 */
    private Long sort;

    /** 选项值（前端绑定 value，统一 String）。 */
    private String value;

    /** 显示文案。 */
    private String label;
}
