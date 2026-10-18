/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.emr.ExtensionOut;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 门诊文书跨患者分页出参。
 */
@Getter
@Setter
public class EmrOutpatientDocumentPageExtensionOut {

    /** 当前页行。 */
    private List<EmrOutpatientDocumentItemExtensionOut> content = new ArrayList<>();

    /** 总条数。 */
    private long totalCount;

    /** 页码（1-based）。 */
    private int pageNo = 1;

    /** 每页条数。 */
    private int pageSize = 20;
}
