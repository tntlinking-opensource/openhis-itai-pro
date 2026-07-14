/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.schedule.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 日程分页查询入参。
 */
@Getter
@Setter
public class SchedulePageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;
}
