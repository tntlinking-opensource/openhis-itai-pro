/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.nursestation.encountermanage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 队列行展示增补：读模型未投影字段由 App 批量回填。
 */
@Data
@Accessors(chain = true)
/** 队列展示增补字段。 */
@Schema(description = "队列展示增补字段")
public class NurseStationQueueDisplayEnrichment {

    @Schema(description = "病房名称")
    private String houseName;

    @Schema(description = "拼音码")
    private String pinyinCode;

    /** 五笔检索码。 */
    @Schema(description = "五笔码")
    private String wubiCode;
}
