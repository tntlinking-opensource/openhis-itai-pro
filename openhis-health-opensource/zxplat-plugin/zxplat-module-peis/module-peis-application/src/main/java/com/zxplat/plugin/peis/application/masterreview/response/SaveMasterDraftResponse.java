/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.masterreview.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 保存总检草稿响应（IF-PEIS-MAS-03）。
 */
@Data
public class SaveMasterDraftResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报告主键。 */
    private Long diagnosticReportId;

    /** 状态。 */
    private String statusEnum;
}
