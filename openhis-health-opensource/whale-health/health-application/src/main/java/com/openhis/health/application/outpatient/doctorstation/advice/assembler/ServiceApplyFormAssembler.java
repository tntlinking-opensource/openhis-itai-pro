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

package com.openhis.health.application.outpatient.doctorstation.advice.assembler;

import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceApplyFormDetailItemRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceApplyFormHeaderDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceApplyFormLineDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceApplyFormLineRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceRequestDetailSummaryDto;
import com.openhis.health.module.common.assembler.advice.model.ServiceApplyFormDetailItemSpec;
import com.openhis.health.module.common.assembler.advice.model.ServiceApplyFormHeaderModel;
import com.openhis.health.module.common.assembler.advice.model.ServiceApplyFormLineModel;
import com.openhis.health.module.common.assembler.advice.model.ServiceApplyFormLineSpec;
import com.openhis.health.module.common.assembler.advice.model.ServiceRequestDetailSummaryModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 申请单 HTTP DTO 与 module-support 编排 Spec/Model 互转（IF-DOR-21/22、IF-IP-DOR-23/24）。
 *
 * <p>职责边界：仅做应用层 DTO 与 {@code ServiceApplyFormOrchestrationSupport} 入参/出参模型之间的字段拷贝；
 * 不含领域写库与查询编排（后者在 support 与 AppService）。</p>
 */
@Component
public class ServiceApplyFormAssembler {

    /**
     * 保存请求行 → 编排 Spec。
     *
     * @param lines HTTP 明细行
     * @return 编排入参 Spec 列表
     */
    public List<ServiceApplyFormLineSpec> toLineSpecs(List<ServiceApplyFormLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ServiceApplyFormLineSpec> specs = new ArrayList<>();
        // 逐行拷贝 HTTP 载荷为编排入参 Spec（跳过 null 行）。
        for (ServiceApplyFormLineRequest line : lines) {
            if (line == null) {
                continue;
            }
            ServiceApplyFormLineSpec spec = new ServiceApplyFormLineSpec();
            spec.setActivityDefinitionId(line.getActivityDefinitionId());
            spec.setQuantity(line.getQuantity());
            spec.setUnitCode(line.getUnitCode());
            spec.setRemark(line.getRemark());
            spec.setDetailItems(toDetailItemSpecs(line.getDetailItems()));
            specs.add(spec);
        }
        return specs;
    }

    /**
     * 编排 Model 头 → 列表响应 DTO。
     *
     * @param models support 层分组结果
     * @return HTTP 申请单头列表
     */
    public List<ServiceApplyFormHeaderDto> toHeaderDtos(List<ServiceApplyFormHeaderModel> models) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        List<ServiceApplyFormHeaderDto> dtos = new ArrayList<>();
        // 逐组头 Model 映射为列表响应 DTO。
        for (ServiceApplyFormHeaderModel model : models) {
            if (model == null) {
                continue;
            }
            ServiceApplyFormHeaderDto dto = new ServiceApplyFormHeaderDto();
            dto.setPrescriptionNo(model.getPrescriptionNo());
            dto.setCategoryEnum(model.getCategoryEnum());
            dto.setOrgId(model.getOrgId());
            dto.setOrgName(model.getOrgName());
            dto.setAuthoredAt(model.getAuthoredAt());
            dto.setStatusSummary(model.getStatusSummary());
            dto.setLines(toLineDtos(model.getLines()));
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * 明细摘要 Model → 医嘱列表/外发 DTO。
     *
     * @param models 领域读侧解析后的明细摘要
     * @return HTTP 明细摘要列表
     */
    public List<ServiceRequestDetailSummaryDto> toDetailSummaryDtos(List<ServiceRequestDetailSummaryModel> models) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        List<ServiceRequestDetailSummaryDto> dtos = new ArrayList<>();
        // 明细摘要供申请单列表、医嘱统一列表与外发网关复用。
        for (ServiceRequestDetailSummaryModel model : models) {
            if (model == null) {
                continue;
            }
            ServiceRequestDetailSummaryDto dto = new ServiceRequestDetailSummaryDto();
            dto.setTargetTable(model.getTargetTable());
            dto.setTargetId(model.getTargetId());
            dto.setTargetDisplayName(model.getTargetDisplayName());
            dto.setRemark(model.getRemark());
            dtos.add(dto);
        }
        return dtos;
    }

    private List<ServiceApplyFormLineDto> toLineDtos(List<ServiceApplyFormLineModel> models) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        List<ServiceApplyFormLineDto> dtos = new ArrayList<>();
        for (ServiceApplyFormLineModel model : models) {
            if (model == null) {
                continue;
            }
            ServiceApplyFormLineDto dto = new ServiceApplyFormLineDto();
            dto.setRequestId(model.getRequestId());
            dto.setActivityDefinitionId(model.getActivityDefinitionId());
            dto.setActivityName(model.getActivityName());
            dto.setQuantity(model.getQuantity());
            dto.setUnitCode(model.getUnitCode());
            dto.setUnitName(model.getUnitName());
            dto.setRemark(model.getRemark());
            dto.setDetails(toDetailSummaryDtos(model.getDetails()));
            dtos.add(dto);
        }
        return dtos;
    }

    private List<ServiceApplyFormDetailItemSpec> toDetailItemSpecs(List<ServiceApplyFormDetailItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<ServiceApplyFormDetailItemSpec> specs = new ArrayList<>();
        for (ServiceApplyFormDetailItemRequest item : items) {
            if (item == null) {
                continue;
            }
            ServiceApplyFormDetailItemSpec spec = new ServiceApplyFormDetailItemSpec();
            spec.setTargetTable(item.getTargetTable());
            spec.setTargetId(item.getTargetId());
            spec.setRemark(item.getRemark());
            specs.add(spec);
        }
        return specs;
    }
}
