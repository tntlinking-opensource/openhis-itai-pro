/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.domain.imagingstudy.command;

import lombok.Getter;
import lombok.Setter;

/**
 * C-STORE 回写绑定 ImagingStudy 命令（语义同 ensureStudy）。
 */
@Getter
@Setter
public class BindImagingStudyFromCStoreCommand {

    /** 检查工单主键。 */
    private Long workItemId;

    /** DICOM Study Instance UID。 */
    private String studyInstanceUid;

    /** 主 Series Instance UID；可选。 */
    private String seriesInstanceUid;

    /** 冗余检查号；可选。 */
    private String accessionNumber;

    /** 模态码；可选。 */
    private String modalityCode;

    /** 租户主键。 */
    private Long tenantId;

    /** 操作人主键。 */
    private Long operatorId;
}
