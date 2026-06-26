/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

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
