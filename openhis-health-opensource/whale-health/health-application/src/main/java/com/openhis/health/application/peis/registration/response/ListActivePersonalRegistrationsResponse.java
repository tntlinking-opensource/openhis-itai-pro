/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.response;

import com.openhis.health.application.peis.registration.dto.PersonalRegistrationHistoryRowDto;
import lombok.Data;

import java.util.List;

/**
 * 患者在检体检单列表响应。
 */
@Data
public class ListActivePersonalRegistrationsResponse {

    /** 在检体检单。 */
    private List<PersonalRegistrationHistoryRowDto> rows;
}
