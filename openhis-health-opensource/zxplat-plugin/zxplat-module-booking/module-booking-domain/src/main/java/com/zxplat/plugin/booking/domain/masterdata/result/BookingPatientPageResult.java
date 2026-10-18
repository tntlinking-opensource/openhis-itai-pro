/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.masterdata.result;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 患者分页检索结果。
 */
@Getter
@Setter
public class BookingPatientPageResult {

    /** 当前页数据。 */
    private List<BookingPatientItem> content;

    /** 总记录数。 */
    private long totalCount;

    /** 当前页码（1-based）。 */
    private int pageNo;

    /** 页大小。 */
    private int pageSize;
}
