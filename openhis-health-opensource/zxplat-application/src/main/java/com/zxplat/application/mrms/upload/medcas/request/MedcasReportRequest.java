/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.medcas.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 4101A/4102/一键上报共用病案+合同入参。
 */
@Getter
@Setter
public class MedcasReportRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 费用性质合同业务号。 */
    private String contractBusNo;
}
