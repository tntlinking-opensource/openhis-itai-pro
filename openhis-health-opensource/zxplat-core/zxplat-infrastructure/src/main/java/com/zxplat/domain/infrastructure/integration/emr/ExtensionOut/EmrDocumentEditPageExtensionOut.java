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
 * 文书编辑申请分页出参。
 */
@Getter
@Setter
public class EmrDocumentEditPageExtensionOut {

    /** 总条数。 */
    private Long totalCount;

    /** 行（通常不含双 JSON）。 */
    private List<EmrDocumentEditApplicationExtensionOut> content = new ArrayList<>();
}
