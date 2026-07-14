/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.document.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ValidateDocumentResponse {

    /** errors（业务字段） */
    private List<ValidateDocumentErrorItem> errors = new ArrayList<>();

    @Getter
    @Setter
    public static class ValidateDocumentErrorItem {
        /** dataItem 名称 */
        private String dataItemName;
        /** 业务编码（唯一） */
        private String code;
        /** message（业务字段） */
        private String message;
    }
}
