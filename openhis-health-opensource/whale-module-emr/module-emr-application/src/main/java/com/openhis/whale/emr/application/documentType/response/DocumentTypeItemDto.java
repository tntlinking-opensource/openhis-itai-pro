/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.documentType.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 文书类型下拉项
 */
@Getter
@Setter
public class DocumentTypeItemDto {

    /** 业务编码（唯一） */
    private String code;
    /** 名称 */
    private String name;
}
