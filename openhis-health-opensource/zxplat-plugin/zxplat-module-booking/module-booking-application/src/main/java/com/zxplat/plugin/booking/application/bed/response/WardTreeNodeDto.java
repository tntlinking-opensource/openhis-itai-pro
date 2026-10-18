/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 病区树节点 DTO。
 */
@Getter
@Setter
public class WardTreeNodeDto {

    /** 排序序号。 */
    private Long sort;

    /** 节点值（地点主键）。 */
    private String value;

    /** 节点显示标签。 */
    private String label;

    /** 子节点列表。 */
    private List<WardTreeNodeDto> children;
}
