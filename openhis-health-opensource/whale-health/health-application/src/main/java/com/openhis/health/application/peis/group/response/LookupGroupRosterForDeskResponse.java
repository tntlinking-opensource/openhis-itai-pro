/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.response;

import com.openhis.health.application.peis.group.dto.GroupRosterDeskContextDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 登记台团检名册查询响应。
 */
@Data
public class LookupGroupRosterForDeskResponse {

    private List<GroupRosterDeskContextDto> matches = new ArrayList<>();
}
