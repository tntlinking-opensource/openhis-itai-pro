/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.writeoff.request;

import lombok.Data;

import java.time.LocalDate;

/**
 * 手动冲红页列表查询。
 */
@Data
public class PageWriteOffDocumentsRequest {

    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String keyword;
    private String billNo;
    private String issueKind;
    /**
     * 列表范围：{@code CANDIDATE}=待冲红（已开具且未成功冲红）；{@code HISTORY}=冲红记录。
     */
    private String scope;
    private Integer pageNo = 1;
    private Integer pageSize = 20;
}
