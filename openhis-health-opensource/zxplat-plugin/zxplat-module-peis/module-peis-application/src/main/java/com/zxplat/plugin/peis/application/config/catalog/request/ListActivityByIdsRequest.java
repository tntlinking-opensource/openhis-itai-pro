/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.catalog.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 按主键批量读取体检项目（套餐编辑回显补全用）。
 */
@Data
public class ListActivityByIdsRequest {

    /** 项目主键列表。 */
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> activityIds;
}
