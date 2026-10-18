/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.volume.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 卷宗目录 DTO。
 */
@Getter
@Setter
public class VolumeCatalogDto {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 院内病案号。 */
    private String medcasno;

    /** 首页目录项。 */
    private VolumeItemDto homepageItem;

    /** EMR 文档目录项列表。 */
    private List<VolumeItemDto> documents = new ArrayList<>();
}
