/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.peis.response;

import java.util.ArrayList;
import java.util.List;

import com.openhis.health.domain.share.extension.peis.PeisGroupRosterDeskView;

import lombok.Getter;
import lombok.Setter;

/**
 * 小程序：团检名册查询响应。
 */
@Getter
@Setter
public class MiniappLookupRosterResponse {

    private List<PeisGroupRosterDeskView> matches = new ArrayList<>();
}
