/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.document.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveDocumentResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    /** 文书主键 id */
    private Long documentId;
    /** 本次保存产生的新版本号（未存版本为 0） */
    private Integer newVersionNumber;
}
