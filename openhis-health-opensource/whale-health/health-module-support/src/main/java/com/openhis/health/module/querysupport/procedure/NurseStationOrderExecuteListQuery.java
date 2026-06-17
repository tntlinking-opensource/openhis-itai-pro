/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.procedure;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医嘱执行列表读查询（IF-IP-EXE-01），供 Loader 与 report-bridge 共用。
 */
@Data
@Accessors(chain = true)
public class NurseStationOrderExecuteListQuery {

    private List<Long> encounterIds;
    private String executeTab;
    private String therapyType;
    private LocalDateTime executionDeadline;
}
