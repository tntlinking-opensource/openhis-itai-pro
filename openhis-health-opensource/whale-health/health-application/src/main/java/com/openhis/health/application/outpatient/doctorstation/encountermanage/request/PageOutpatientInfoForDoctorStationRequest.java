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

package com.openhis.health.application.outpatient.doctorstation.encountermanage.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 医生站分页查询门诊就诊患者列表请求（IF-DOR-01）。
 * <p>分页与通用检索字段见 {@link AbstractPageQuery}（B.10.11）。科室组织标识由服务端从登录会话解析并写入读模型查询，不在请求体上传。</p>
 * <p>{@code encounterStatus} 可空（缺省多队列并集）；{@code staffPractitionerId} 必填（BR-DOR-009 可见性过滤）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
/** 医生站-分页查询就诊患者列表请求。 */
@Schema(description = "医生站-分页查询就诊患者列表请求")
public class PageOutpatientInfoForDoctorStationRequest extends AbstractHealthPageRequest {

    /**
     * 就诊状态（与读模型 {@code statusEnum} 一致；待诊传已挂号 {@link EncounterStatus#REGISTERED}）。
     * <p>可空：未传时由应用映射为已挂号/在诊/暂离/诊毕并集（{@link EncounterStatus#REGISTERED}、
     * {@link EncounterStatus#IN_PROGRESS}、{@link EncounterStatus#ON_HOLD}、{@link EncounterStatus#DISCHARGED}）；
     * 医师可见性由 {@link #staffPractitionerId} 与 BR-DOR-009 在读侧判定。</p>
     */
    @Schema(description = "就诊状态队列；不传则默认已挂号+在诊+暂离+诊毕并集")
    private EncounterStatus encounterStatus;

    /**
     * 当前登录医师主键；必填，映射为读模型 {@code doctorStationViewerPractitionerId}（BR-DOR-009）。
     */
    @NotNull(message = "当前登录医师主键不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "当前登录医师主键（必填；待诊按挂号医生、在诊/暂离按接诊医生、诊毕科室共享）",
        requiredMode = Schema.RequiredMode.REQUIRED)
    private Long staffPractitionerId;

    @Override
    @Schema(description = "检索关键字（姓名/就诊号等）")
    public String getKeyword() {
        return super.getKeyword();
    }

    @Override
    @NotNull(message = "页码不能为空")
    /** 页码。 */
    @Schema(description = "页码（从 1 开始）", requiredMode = Schema.RequiredMode.REQUIRED)
    public Integer getPageNo() {
        return super.getPageNo();
    }

    @Override
    @NotNull(message = "页大小不能为空")
    /** 每页条数。 */
    @Schema(description = "每页条数", requiredMode = Schema.RequiredMode.REQUIRED)
    public Integer getPageSize() {
        return super.getPageSize();
    }

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 日期范围起。 */
    @Schema(description = "日期范围起（挂号/接诊日）")
    public LocalDateTime getStartDate() {
        return super.getStartDate();
    }

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 日期范围止。 */
    @Schema(description = "日期范围止")
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
