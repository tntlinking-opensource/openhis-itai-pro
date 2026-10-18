/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.document.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 编辑器图片上传结果：{@link #url} 为站点相对路径，前端拼站点根或走 /uploads 静态映射。
 */
@Getter
@Setter
public class EditorImageUploadDto {

    /** 如 /uploads/emr/2026/05/26/abc.png */
    private String url;
}
