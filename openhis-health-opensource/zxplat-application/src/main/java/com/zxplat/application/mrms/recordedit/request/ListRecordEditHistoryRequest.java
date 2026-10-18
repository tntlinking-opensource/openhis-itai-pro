/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.recordedit.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 编辑历史（IF-MRMS-EDIT-09）。
 */
@Getter
@Setter
public class ListRecordEditHistoryRequest {

    /** document / homepage。 */
    private String targetKind;

    /** 文书主键。 */
    private Long documentId;

    /** 病案主键。 */
    private Long caseRecordId;
}
