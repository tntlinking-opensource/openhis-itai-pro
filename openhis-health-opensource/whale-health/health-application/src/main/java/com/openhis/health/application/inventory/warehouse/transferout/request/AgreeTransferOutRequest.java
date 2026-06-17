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

package com.openhis.health.application.inventory.warehouse.transferout.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 *
 * <p>触发同意链：领域同意、发放、源库扣减、目的入账、药房入库壳单与追溯写入由应用服务编排，单事务边界见 AS-SDD。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class AgreeTransferOutRequest {

    @Schema(description = "出库业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /**
     * 已废弃：领域 {@code agreeRequest} 内以服务端当前时间写入 {@code approval_time}（WHS-IN-SDD-R-013 / WHS-SO-SDD-R-009），请求体若仍带此字段则忽略。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "已废弃：审批时间由服务端写入，可忽略", deprecated = true)
    private LocalDateTime approvalTime;

    /**
     * 已废弃：Mapper 固定以当前操作人 {@code operatorId} 写入 {@code approver_id}，请求体若仍带此字段则忽略。
     */
    @Schema(description = "已废弃：审批人取当前登录用户，可忽略", deprecated = true)
    private Long approverId;

    /** @deprecated CHS 模块启用即上传，此开关不再生效。 */
    @Deprecated
    @Schema(description = "同意后是否上传医保进销存")
    private Boolean medicareUploadEnabled;

    @Schema(description = "医保契约 busNo")
    private String contractBusNo;
}
