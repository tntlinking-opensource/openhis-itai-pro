/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.application.document.response;

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
