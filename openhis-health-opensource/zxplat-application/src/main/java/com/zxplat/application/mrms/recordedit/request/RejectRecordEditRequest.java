/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.recordedit.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 驳回编辑申请（IF-MRMS-EDIT-08）。
 */
@Getter
@Setter
public class RejectRecordEditRequest {

    /** document / homepage。 */
    private String targetKind;

    /** 申请主键。 */
    private Long applicationId;

    /** 驳回原因。 */
    private String rejectReason;
}
