/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.masterdata.result;

import com.zxplat.plugin.booking.domain.masterdata.model.MasterDataOption;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 门诊排班台初始化主数据快照（2.1）。
 */
@Getter
@Setter
public class ScheduleFormMasterDataSnapshot {

    /** 科室（Organization）下拉。 */
    private List<MasterDataOption> departments;

    /** 医师下拉（可按科室收窄）。 */
    private List<MasterDataOption> practitioners;

    /** 诊室/位置下拉。 */
    private List<MasterDataOption> clinicLocations;

    /** 号源类型（HealthcareService）下拉。 */
    private List<MasterDataOption> healthcareServices;
}
