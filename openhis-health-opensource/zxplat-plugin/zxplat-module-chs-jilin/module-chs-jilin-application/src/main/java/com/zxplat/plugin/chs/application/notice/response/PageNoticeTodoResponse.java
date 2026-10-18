/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.response;

import com.zxplat.plugin.chs.application.notice.dto.NoticeTodoRowDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 催办待办分页响应（IF-CHS-CAT-005-R-04）。
 */
@Data
public class PageNoticeTodoResponse {
    /** OPEN 停用/甲乙类待办汇总。 */
    private NoticeTodoSummaryResponse summary;
    /** 当前过滤条件下总条数。 */
    private long total;
    /** 当前页待办行。 */
    private List<NoticeTodoRowDto> records = new ArrayList<>();
}
