/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 首次批初始化汇总出参 */
@Data
@Accessors(chain = true)
public class InitializeInventoryResult {

    private int attemptedPairs;
    private int succeededPairs;
    private int failedPairs;
    private int skippedNoYbNo;
    private int skippedAlreadyInitialized;
    private Integer pageNo;
    private Integer nextPageNo;
    private Boolean hasMore;
    private Long totalElements;
    private Integer totalPages;
    private List<InitializeInventoryLineResult> lines = new ArrayList<>();
}
