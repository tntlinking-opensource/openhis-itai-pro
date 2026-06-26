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

package com.openhis.health.application.inventory.pharmacy.stocktake.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 审批同意盘点单入参（IF-PHA-ST-08）。
 */
@Data
@Accessors(chain = true)
/** 审批同意盘点 IF-PHA-ST-08。 */
@Schema(description = "审批同意盘点 IF-PHA-ST-08")
public class AgreePharmacyStocktakeRequest {

    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 已废弃：审批时间由服务端写入。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 已废弃：审批时间由服务端写入。 */
    @Schema(description = "已废弃：审批时间由服务端写入", deprecated = true)
    private LocalDateTime approvalTime;

    /** 已废弃：审批人取当前操作人。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 已废弃：审批人取当前登录用户。 */
    @Schema(description = "已废弃：审批人取当前登录用户", deprecated = true)
    private Long approverId;

    /** @deprecated CHS 模块启用即上传，此开关不再生效。 */
    @Deprecated
    /** 同意后是否上传医保进销存。 */
    @Schema(description = "同意后是否上传医保进销存")
    private Boolean medicareUploadEnabled;

    /** 医保契约 busNo。 */
    @Schema(description = "医保契约 busNo")
    private String contractBusNo;
}
