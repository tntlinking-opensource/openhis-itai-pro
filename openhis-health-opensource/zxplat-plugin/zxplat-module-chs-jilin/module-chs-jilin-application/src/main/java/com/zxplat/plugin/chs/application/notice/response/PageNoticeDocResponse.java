/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.response;

import com.zxplat.plugin.chs.application.notice.dto.NoticeDocRowDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件列表分页响应（IF-CHS-CAT-005-R-01）。
 */
@Data
public class PageNoticeDocResponse {

    /** 总条数。 */
    private long total;

    /** 当前页记录。 */
    private List<NoticeDocRowDto> records = new ArrayList<>();
}
