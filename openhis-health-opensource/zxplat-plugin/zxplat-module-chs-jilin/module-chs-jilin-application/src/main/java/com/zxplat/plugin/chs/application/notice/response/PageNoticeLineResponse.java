/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.response;

import com.zxplat.plugin.chs.application.notice.dto.NoticeLineRowDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件明细分页响应（IF-CHS-CAT-005-R-02）。
 */
@Data
public class PageNoticeLineResponse {

    /** 总条数。 */
    private long total;

    /** 当前页记录。 */
    private List<NoticeLineRowDto> records = new ArrayList<>();
}
