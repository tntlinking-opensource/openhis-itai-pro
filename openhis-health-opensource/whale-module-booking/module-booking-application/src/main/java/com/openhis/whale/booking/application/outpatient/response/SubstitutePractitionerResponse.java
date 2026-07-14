/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 替诊结果。
 */
@Getter
@Setter
public class SubstitutePractitionerResponse {

    /** 日程主键。 */
    private Long scheduleId;

    /** 原出诊医师主键。 */
    private Long originalPractitionerId;

    /** 替诊医师主键。 */
    private Long substitutePractitionerId;

    /** 替诊原因。 */
    private String substituteReason;

    /** 是否已撤销替诊（substitutePractitionerId 为空时 true）。 */
    private Boolean cleared;
}
