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

package com.openhis.whale.emr.application.pdf.model;

import com.openhis.whale.emr.application.pdf.config.PrintConfig;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenPDF 渲染入参（由 GeneratePdfRequest 转换）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class PdfRenderOptions {

    private PrintConfig printConfig;
    private String pageRange = "all";
    private List<String> regionNames;
    private boolean includeTrackChanges;
    private boolean previewMode = true;
    private int previewMaxPages = 20;
    private String documentTitle;
    private String documentTypeName;
    private Map<String, String> metadata = new HashMap<>();

    public Map<String, String> safeMetadata() {
        return metadata != null ? metadata : Map.of();
    }
}
