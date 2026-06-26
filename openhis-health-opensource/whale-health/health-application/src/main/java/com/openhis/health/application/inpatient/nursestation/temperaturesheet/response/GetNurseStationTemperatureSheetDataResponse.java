/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.temperaturesheet.response;

import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetDerivedMetaDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetFillSlotDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetGridRowDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetPaginationDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetTemperaturePointDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-TMP-02：体温单曲线+网格+分页+回显响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单数据响应")
public class GetNurseStationTemperatureSheetDataResponse {

    /** 曲线点（仅主表体温）。 */
    @Schema(description = "曲线点")
    private List<TemperatureSheetTemperaturePointDto> temperaturePoints = new ArrayList<>();

    /** 日×6 槽宽表行。 */
    @Schema(description = "网格行")
    private List<TemperatureSheetGridRowDto> gridRows = new ArrayList<>();

    /** 分页窗口。 */
    @Schema(description = "分页")
    private TemperatureSheetPaginationDto pagination;

    /** 住院天数/周次/术后天数等派生元数据。 */
    @Schema(description = "派生元数据")
    private TemperatureSheetDerivedMetaDto derivedMeta;

    /** focusRecordDate 有值时的 6 槽+补充项回显。 */
    @Schema(description = "录入槽回显")
    private List<TemperatureSheetFillSlotDto> fillSlots = new ArrayList<>();
}
