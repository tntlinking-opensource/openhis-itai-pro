/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.domain.imagingstudy.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * ImagingStudy 读模型。
 */
@Getter
@Setter
public class ImagingStudyQueryModel {

    /** 主键。 */
    private Long id;

    /** 检查工单主键。 */
    private Long workItemId;

    /** Study Instance UID。 */
    private String studyInstanceUid;

    /** Series Instance UID。 */
    private String seriesInstanceUid;

    /** 冗余检查号。 */
    private String accessionNumber;

    /** 模态码。 */
    private String modalityCode;
}
