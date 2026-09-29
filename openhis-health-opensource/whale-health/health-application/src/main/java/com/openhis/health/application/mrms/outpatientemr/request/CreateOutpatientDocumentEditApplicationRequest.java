/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.outpatientemr.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建门诊文书编辑申请（IF-MRMS-OEMR-05，增量）。
 */
@Getter
@Setter
public class CreateOutpatientDocumentEditApplicationRequest {

    /** 文书主键。 */
    private Long documentId;

    /** 申请原因。 */
    private String applyReason;
}
