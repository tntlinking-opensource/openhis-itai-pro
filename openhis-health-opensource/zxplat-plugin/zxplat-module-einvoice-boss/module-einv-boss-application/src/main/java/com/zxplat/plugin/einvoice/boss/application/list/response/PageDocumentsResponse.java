/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.list.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 电子发票分页查询响应。
 */
@Data
public class PageDocumentsResponse {

    private List<BossDocumentListItem> rows = new ArrayList<>();
    private long total;
    /** keyword 过滤命中上限时为 true，结果可能不完整。 */
    private boolean keywordTruncated;
}
