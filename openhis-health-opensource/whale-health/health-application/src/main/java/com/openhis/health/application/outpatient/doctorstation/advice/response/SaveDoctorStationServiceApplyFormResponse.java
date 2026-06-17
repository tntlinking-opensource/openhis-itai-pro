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

package com.openhis.health.application.outpatient.doctorstation.advice.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-21 / IF-IP-DOR-23：保存申请单响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存申请单响应")
public class SaveDoctorStationServiceApplyFormResponse {

    /** 新申请单号（{@code AR} 前缀；编辑保存亦为取新号）。 */
    @Schema(description = "新申请单号（AR 前缀）")
    private String prescriptionNo;

    /** 本单新建 ServiceRequest 主键列表，顺序与请求 {@code lines} 一致。 */
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    @Schema(description = "本单新建 ServiceRequest 主键列表")
    private List<Long> requestIds;
}
