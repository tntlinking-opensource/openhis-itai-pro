/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.frequency.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 频次计划执行时点计算结果。 */
@Data
public class ComputeFrequencyExecutionTimePointsResponse {

    /** 执行次数（时点个数）。 */
    private Integer executionCount;

    /** 升序执行时刻 ISO 串列表。 */
    private List<String> executionAtList = new ArrayList<>();
}
