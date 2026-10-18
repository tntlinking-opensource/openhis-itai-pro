/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.document.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class InitEmrPageResponse {

    /** 工作页可选文书类型 code 列表 */
    private List<String> availableDocumentTypes;
    /** 打印选项（预留） */
    private List<String> printOptions;
}
