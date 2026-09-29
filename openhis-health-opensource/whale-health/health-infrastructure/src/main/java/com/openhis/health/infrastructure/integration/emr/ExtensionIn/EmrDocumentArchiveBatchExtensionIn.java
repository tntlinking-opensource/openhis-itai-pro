/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 文书批量提交归档入参。
 */
@Getter
@Setter
public class EmrDocumentArchiveBatchExtensionIn {

    /** 文书主键列表。 */
    private List<Long> documentIds;

    /** 提交人。 */
    private Long operatorId;
}
