/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.catalog.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 批量校验目录编码（clone / 上传组装）。 */
@Data
@Accessors(chain = true)
public class ElepCatalogValidateCommand {

    public static final String KIND_DRUG = "DRUG";
    public static final String KIND_DIAGNOSIS = "DIAGNOSIS";

    private Long contractId;
    private String contractBusNo;
    /** {@link #KIND_DRUG} 或 {@link #KIND_DIAGNOSIS} */
    private String catalogKind;
    private List<Line> lines = new ArrayList<>();

    @Data
    @Accessors(chain = true)
    public static class Line {

        private int lineNo;
        private String hilistCode;
        private String aprvno;
        private String spec;
    }
}
