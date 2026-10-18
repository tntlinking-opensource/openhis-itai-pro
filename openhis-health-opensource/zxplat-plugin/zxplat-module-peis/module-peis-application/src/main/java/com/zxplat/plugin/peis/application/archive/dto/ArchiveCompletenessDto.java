/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.archive.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ArchiveCompletenessDto {
    /** 无 block 级缺失即为 complete */
    private Boolean complete;
    /** 最高缺失级别：ok / info / warn / block */
    private String level;
    private String summary;
    private List<ArchiveMissingItemDto> missingItems = new ArrayList<>();
}
