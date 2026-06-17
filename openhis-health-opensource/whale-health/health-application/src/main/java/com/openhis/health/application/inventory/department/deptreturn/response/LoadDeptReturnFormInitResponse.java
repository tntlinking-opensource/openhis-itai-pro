/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.department.deptreturn.response;

import com.openhis.health.application.inventory.department.deptreturn.dto.WarehouseOptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LoadDeptReturnFormInitResponse {

    /** 可选源科室库位列表。 */
    private List<WarehouseOptionDto> sourceDeptLocationOptions;
    /** 可选目的药库列表。 */
    private List<WarehouseOptionDto> purposeWarehouseOptions;
}
