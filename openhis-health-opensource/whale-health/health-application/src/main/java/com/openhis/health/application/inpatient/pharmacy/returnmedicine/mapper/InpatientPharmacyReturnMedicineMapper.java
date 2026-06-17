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

package com.openhis.health.application.inpatient.pharmacy.returnmedicine.mapper;

import com.openhis.health.application.inpatient.pharmacy.returnmedicine.dto.InpatientReturnMedicineDetailRowDto;
import com.openhis.health.application.inpatient.pharmacy.returnmedicine.dto.InpatientReturnPatientRowDto;
import com.openhis.health.application.inpatient.pharmacy.returnmedicine.request.GetInpatientReturnMedicineDetailRequest;
import com.openhis.health.application.inpatient.pharmacy.returnmedicine.request.PageInpatientReturnPatientRequest;
import com.openhis.health.application.inpatient.pharmacy.returnmedicine.response.LoadInpatientReturnMedicineInitResponse;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.InpatientPharmacyReturnEncounterPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.ReturnMedicineDetailQuery;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicineDetailReadRow;
import com.openhis.whale.core.application.dto.OptionDto;

import java.util.List;

/**
 * 住院药房退药应用层映射器：请求→领域查询、读模型行→对外 DTO；禁止承载业务流程门控。
 */
public interface InpatientPharmacyReturnMedicineMapper {

    /**
     * 装配初始化响应。
     *
     * @param pharmacyOptions     药房下拉
     * @param wardOptions         病区下拉
     * @param refundStatusOptions 退药状态下拉
     * @return 初始化响应
     */
    LoadInpatientReturnMedicineInitResponse toLoadInitResponse(
        List<OptionDto> pharmacyOptions,
        List<OptionDto> wardOptions,
        List<OptionDto> refundStatusOptions);

    /**
     * 装配退药状态下拉项（固定 PENDING_REFUND/REFUNDED 子集）。
     *
     * @return 非空列表
     */
    List<OptionDto> mapToRefundStatusOptions();

    /**
     * 将分页请求映射为住院退药患者队列查询。
     *
     * @param request 应用请求；调用方保证非空
     * @return 领域查询入参
     */
    InpatientPharmacyReturnEncounterPageQuery toPageQuery(PageInpatientReturnPatientRequest request);

    /**
     * 将住院就诊上下文与聚合退药态映射为队列行 DTO。
     *
     * @param ctx             住院就诊上下文；调用方保证非空
     * @param refundEnumCode  聚合退药态 code
     * @return 队列行 DTO
     */
    InpatientReturnPatientRowDto toReturnPatientRow(InpatientEncounterContextQueryModel ctx, String refundEnumCode);

    /**
     * 将明细请求映射为领域查询。
     *
     * @param request 应用请求；调用方保证非空
     * @return 领域查询入参
     */
    ReturnMedicineDetailQuery toDetailQuery(GetInpatientReturnMedicineDetailRequest request);

    /**
     * 将退药明细读行映射为对外 DTO 列表。
     *
     * @param rows 读侧装配行；允许为空
     * @return DTO 列表；空入参返回空列表
     */
    List<InpatientReturnMedicineDetailRowDto> toDetailRows(List<ReturnMedicineDetailReadRow> rows);
}
