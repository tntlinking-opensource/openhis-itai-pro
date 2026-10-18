/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.bed.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 床位矩阵单元格 DTO。
 */
@Getter
@Setter
public class BedMatrixCellDto {

    /** 床位地点主键。 */
    private Long bedLocationId;

    /** 床位名称。 */
    private String bedName;

    /** 日期。 */
    private String date;

    /** 运营状态编码。 */
    private String operationalCode;

    /** 展示状态文案。 */
    private String displayStatus;

    /** 关联预约主键。 */
    private Long appointmentId;

    /** 患者姓名。 */
    private String patientName;
}
