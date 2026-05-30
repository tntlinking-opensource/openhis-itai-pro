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

package com.openhis.health.application.emergency.doctorstation.encounter.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 急诊医生站：按就诊主数据分页检索请求（不经门诊信息读模型）。
 * <p>科室组织取自登录会话，由应用层下推到领域查询。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "急诊医生站-分页查询就诊主数据请求")
public class PageEmergencyEncountersForDoctorStationRequest extends AbstractPageQuery {

    @Schema(description = "就诊状态编码列表（如 REGISTERED、TRIAGED）；未传时默认待诊队列")
    private List<String> encounterStatusIn;

    @Override
    @NotNull(message = "页码不能为空")
    @Schema(description = "页码（从 1 开始）", requiredMode = Schema.RequiredMode.REQUIRED)
    public Integer getPageNo() {
        return super.getPageNo();
    }

    @Override
    @NotNull(message = "每页条数不能为空")
    @Schema(description = "每页条数", requiredMode = Schema.RequiredMode.REQUIRED)
    public Integer getPageSize() {
        return super.getPageSize();
    }

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "日期范围起（按就诊 startTime）")
    public LocalDateTime getStartDate() {
        return super.getStartDate();
    }

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "日期范围止（按就诊 startTime）")
    public LocalDateTime getEndDate() {
        return super.getEndDate();
    }

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    public void setStartDate(LocalDateTime startDate) {
        super.setStartDate(startDate);
    }

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    public void setEndDate(LocalDateTime endDate) {
        super.setEndDate(endDate);
    }
}
