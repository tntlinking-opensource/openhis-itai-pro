/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.medcas.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 4401 单条上传入参。
 */
@Getter
@Setter
public class Upload4401Request {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 医保合约业务号。 */
    private String contractBusNo;
}
