/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.borrow.request;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 借阅列表分页查询。
 */
@Getter
@Setter
public class QueryBorrowPageRequest extends AbstractPageQuery {

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 借阅状态过滤。 */
    private String statusCode;
    /** 病案主档主键过滤。 */
    private Long caseRecordId;
}
