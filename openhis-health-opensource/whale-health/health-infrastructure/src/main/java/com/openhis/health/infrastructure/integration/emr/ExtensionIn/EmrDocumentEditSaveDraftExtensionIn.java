/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 文书编辑申请暂存入参。
 */
@Getter
@Setter
public class EmrDocumentEditSaveDraftExtensionIn {

    /** 申请主键。 */
    private Long applicationId;

    /** 工作稿 JSON。 */
    private String newContentJson;

    /** 操作人。 */
    private Long operatorId;
}
