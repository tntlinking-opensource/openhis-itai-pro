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

package com.openhis.health.application.inventory.pharmacy.stockin.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 审批同意药房入库（手工单）请求（IF-PHA-IN-08）。
 *
 */
@Getter
@Setter
@Accessors(chain = true)
/** 审批同意药房入库 IF-PHA-IN-08。 */
@Schema(description = "审批同意药房入库 IF-PHA-IN-08", requiredProperties = {"busNo"})
public class AgreePharmacyStockInRequest {

    @NotBlank(message = "busNo 不能为空")
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /**
     * 已废弃：领域 agreeRequest 内以服务端当前时间写入 approval_time，请求体若仍带此字段则忽略。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "已废弃：审批时间由服务端写入，可忽略", deprecated = true)
    private LocalDateTime approvalTime;

    /**
     * 已废弃：同意命令装配固定以当前操作人 operatorId 写入 approver_id，请求体若仍带此字段则忽略。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "已废弃：审批人取当前登录用户，可忽略", deprecated = true)
    private Long approverId;

    /** @deprecated CHS 模块启用即上传，此开关不再生效。 */
    @Deprecated
    @Schema(description = "同意后是否上传医保进销存")
    private Boolean medicareUploadEnabled;

    @Schema(description = "医保契约 busNo")
    private String contractBusNo;
}
