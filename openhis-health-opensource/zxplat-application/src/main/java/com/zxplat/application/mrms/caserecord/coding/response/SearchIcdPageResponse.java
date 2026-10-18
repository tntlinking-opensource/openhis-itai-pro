/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.coding.response;

import com.zxplat.application.mrms.caserecord.coding.dto.IcdItemDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * ICD 选码分页结果（手术下拉：空关键字亦可浏览）。
 */
@Getter
@Setter
public class SearchIcdPageResponse {

    /** 当前页候选项。 */
    private List<IcdItemDto> items = new ArrayList<>();

    /** 总条数（供 SelectTable 分页）。 */
    private long totalCount;
}
