/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.archiveapproval.request;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 待归档审批分页请求（IF-MRMS-APR-01）。
 */
@Getter
@Setter
public class PagePendingArchiveRequest extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 业务类别：op_document / ip_document / case_homepage。
     */
    private String bizCategory;
}
