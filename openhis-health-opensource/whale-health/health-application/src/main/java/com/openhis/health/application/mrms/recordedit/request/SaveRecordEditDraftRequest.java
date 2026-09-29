/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.recordedit.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 暂存工作稿（IF-MRMS-EDIT-03）。
 */
@Getter
@Setter
public class SaveRecordEditDraftRequest {

    /** document / homepage。 */
    private String targetKind;

    /** 申请主键。 */
    private Long applicationId;

    /** 工作稿 JSON。 */
    private String newContentJson;
}
