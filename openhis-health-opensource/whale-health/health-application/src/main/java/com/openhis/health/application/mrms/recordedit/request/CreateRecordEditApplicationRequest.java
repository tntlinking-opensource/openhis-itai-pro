/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.recordedit.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建编辑申请（IF-MRMS-EDIT-01）。
 */
@Getter
@Setter
public class CreateRecordEditApplicationRequest {

    /** document / homepage。 */
    private String targetKind;

    /** 文书主键（document）。 */
    private Long documentId;

    /** 病案主键（homepage）。 */
    private Long caseRecordId;

    /** 申请原因。 */
    private String applyReason;

    /** 门诊/住院 class（document 可选，缺省由应用层补）。 */
    private String encounterClassCode;
}
