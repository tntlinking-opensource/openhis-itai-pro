/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.emr.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 按就诊列出 EMR 文书入参。 */
@Data
@Accessors(chain = true)
public class EmrDocumentListExtensionIn {

    /** 患者主键。 */
    private Long patientId;

    /** 就诊主键。 */
    private Long encounterId;
}
