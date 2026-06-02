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

package com.openhis.health.application.outpatient.pharmacy.returnmedicine.mapper;

import com.openhis.health.application.outpatient.pharmacy.returnmedicine.dto.ReturnMedicineDetailRowDto;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.dto.ReturnPatientRowDto;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.GetReturnMedicineDetailRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.PageReturnPatientRequest;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.dto.DepartmentOptionDto;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.dto.RefundStatusOptionDto;
import com.openhis.health.domain.medication.medicationdispense.query.PageReturnPatientQuery;
import com.openhis.health.domain.medication.medicationdispense.query.ReturnMedicineDetailQuery;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicineDetailReadRow;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicinePatientQueueRow;

import java.util.List;

/**
 * 门诊药房退药应用层映射器：请求→领域查询、读模型行→对外 DTO；禁止承载业务流程门控。
 */
public interface OutpatientPharmacyReturnMedicineMapper {

    /**
     * 将分页请求映射为退药患者队列查询。
     *
     * @param request 应用请求；调用方保证非空
     * @return 领域查询入参
     */
    PageReturnPatientQuery toPageReturnPatientQuery(PageReturnPatientRequest request);

    /**
     * 将明细请求映射为领域查询。
     *
     * @param request 应用请求；调用方保证非空
     * @return 领域查询入参
     */
    ReturnMedicineDetailQuery toReturnMedicineDetailQuery(GetReturnMedicineDetailRequest request);

    /**
     * 将启用机构行映射为科室下拉选项。
     *
     * @param organizations 机构列表；允许为空
     * @return 选项列表；空入参返回空列表
     */
    List<DepartmentOptionDto> mapToDepartmentOptions(List<Organization> organizations);

    /**
     * 装配退药状态下拉项（固定 PENDING_REFUND/REFUNDED 子集）。
     *
     * @return 非空列表
     */
    List<RefundStatusOptionDto> mapToRefundStatusOptions();

    /**
     * 将退药患者队列读行映射为对外 DTO 列表。
     *
     * @param rows 读侧装配行；允许为空
     * @return DTO 列表；空入参返回空列表
     */
    List<ReturnPatientRowDto> mapToReturnPatientRows(List<ReturnMedicinePatientQueueRow> rows);

    /**
     * 将退药明细读行映射为对外 DTO 列表。
     *
     * @param rows 读侧装配行；允许为空
     * @return DTO 列表；空入参返回空列表
     */
    List<ReturnMedicineDetailRowDto> mapToReturnMedicineDetailRows(List<ReturnMedicineDetailReadRow> rows);
}
