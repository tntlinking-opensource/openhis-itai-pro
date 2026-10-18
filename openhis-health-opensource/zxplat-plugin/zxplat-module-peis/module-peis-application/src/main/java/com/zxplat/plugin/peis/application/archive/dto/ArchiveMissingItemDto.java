/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.archive.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ArchiveMissingItemDto {
    /** 缺失编码，见 PeisArchiveCompletenessConstants */
    private String code;
    /** info / warn / block */
    private String level;
    private String message;
}
