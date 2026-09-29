/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 文书归档详情入参。
 */
@Getter
@Setter
public class EmrDocumentArchiveDetailExtensionIn {

    /** 文书主键。 */
    private Long documentId;
}
