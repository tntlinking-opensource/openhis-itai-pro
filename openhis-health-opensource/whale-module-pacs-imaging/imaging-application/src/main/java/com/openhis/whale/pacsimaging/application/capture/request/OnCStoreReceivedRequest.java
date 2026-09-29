/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.request;

import lombok.Getter;
import lombok.Setter;

/**
 * C-STORE 回写请求（IF-PACS-CAP-10）。
 */
@Getter
@Setter
public class OnCStoreReceivedRequest {

    /** 检查号（Accession Number）。 */
    private String accessionNumber;

    /** DICOM Study Instance UID。 */
    private String studyInstanceUid;

    /** 主 Series Instance UID；可选。 */
    private String seriesInstanceUid;

    /** 模态码；可选。 */
    private String modalityCode;
}
