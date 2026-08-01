/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.writeoff.response;

import com.openhis.whale.einvoice.boss.application.list.response.BossDocumentListItem;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 手动冲红页分页结果。
 */
@Data
public class PageWriteOffDocumentsResponse {

    private List<BossDocumentListItem> rows = new ArrayList<>();
    private long total;
    private boolean keywordTruncated;
}
