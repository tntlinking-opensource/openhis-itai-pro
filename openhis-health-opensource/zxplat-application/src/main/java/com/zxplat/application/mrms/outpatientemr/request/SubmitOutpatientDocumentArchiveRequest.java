/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.outpatientemr.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 单份文书提交归档（IF-MRMS-OEMR-03，增量；不改 archiveClinicalCase）。
 */
@Getter
@Setter
public class SubmitOutpatientDocumentArchiveRequest {

    /** 文书主键。 */
    private Long documentId;
}
