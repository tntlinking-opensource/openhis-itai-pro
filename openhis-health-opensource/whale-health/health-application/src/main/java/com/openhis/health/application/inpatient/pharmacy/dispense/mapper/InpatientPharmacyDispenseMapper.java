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

package com.openhis.health.application.inpatient.pharmacy.dispense.mapper;

import com.openhis.health.application.inpatient.pharmacy.dispense.dto.CancelMedicationDispenseLineSubmitDto;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.SavePrepareInpatientDispenseLineSubmitDto;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.CancelInpatientMedicationDispenseResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.DispenseInpatientMedicationsResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.LoadInpatientPharmacyDispensePageInitResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.PageEncounterMedicationDispenseSlipsResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.PagePendingDispenseInpatientEncounterQueueResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.SavePrepareInpatientMedicationDispenseResponse;
import com.openhis.health.module.querysupport.pharmacy.dto.MedicationDispenseSlipPageResult;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.DispenseMedicationCommand;
import com.openhis.health.domain.medication.medicationdispense.command.PrepareMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 住院发药工作站装配器：请求/读模型/领域命令/响应之间的纯字段映射。
 */
public interface InpatientPharmacyDispenseMapper {

    /**
     * IF-IPP-01：装配初始化响应。
     */
    LoadInpatientPharmacyDispensePageInitResponse toLoadInitResponse(
        List<Location> pharmacies,
        List<OptionDto> dispenseStatusOptions,
        List<OptionDto> notPerformedReasonOptions,
        List<OptionDto> wardOptions,
        Page<Practitioner> preparerPage);

    /**
     * IF-IPP-02：装配在院患者队列分页响应。
     */
    PagePendingDispenseInpatientEncounterQueueResponse toInpatientQueuePage(
        Page<Long> encounterIdPage,
        List<InpatientEncounterContextQueryModel> infos);

    /**
     * IF-IPP-03：装配药品发药单分页响应（不含候选库存）。
     */
    PageEncounterMedicationDispenseSlipsResponse toSlipPage(
        Page<MedicationDispenseSlipQueryModel> slipPage,
        Map<Long, MedicationRequestListQueryModel> reqByMedReqId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
        Boolean tcmOnly,
        Boolean westernOnly,
        Map<Long, String> dispensingPractitionerNameByDispenseId,
        String encounterBusNo,
        Map<Long, String> orderingDepartmentNameByOrgId,
        Map<Long, String> orderingPractitionerNameById,
        Map<Long, String> dispenseLocationNameByLocationId,
        Map<Long, String> traceNoEffectiveByDispenseId,
        Map<Long, String> lotNumberEffectiveByDispenseId,
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId,
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId,
        Map<String, String> rateCodeLabelMap,
        Map<String, String> methodCodeLabelMap,
        Map<String, String> doseUnitLabelMap,
        Map<String, String> businessUnitLabelMap,
        Map<String, String> rxFlagLabelMap,
        Map<String, String> partAttributeLabelMap);

    /**
     * 将 querysupport slip 分页结果包装为 IF-IPP-03 响应。
     */
    PageEncounterMedicationDispenseSlipsResponse toSlipPageResponse(MedicationDispenseSlipPageResult result);

    /**
     * IF-IPP-04：装配保存配药命令（库房/批号/数量取自聚合实体）。
     */
    PrepareMedicationDispenseCommand toPrepareMedicationDispenseCommand(
        Long operatorId,
        Long preparerPractitionerId,
        List<SavePrepareInpatientDispenseLineSubmitDto> lines,
        Map<Long, MedicationDispense> dispenseById);

    /**
     * IF-IPP-04：装配保存配药响应。
     */
    SavePrepareInpatientMedicationDispenseResponse toPrepareBatchResponse(List<Long> ids, String message, boolean success);

    /**
     * IF-IPP-05：装配核发命令。
     *
     * @param operatorId      操作人用户主键
     * @param practitionerId  发药药师主键
     * @param dispensers      已校验发放聚合行（与提交顺序一致）
     */
    DispenseMedicationCommand toDispenseCommand(Long operatorId, Long practitionerId, List<MedicationDispense> dispensers);

    /**
     * IF-IPP-05：装配核发响应。
     */
    DispenseInpatientMedicationsResponse toDispenseBatchResponse(List<Long> ids, String message, boolean success);

    /**
     * IF-IPP-06：装配作废命令。
     */
    CancelMedicationDispenseCommand toCancelMedicationDispenseCommand(
        Long operatorId,
        List<CancelMedicationDispenseLineSubmitDto> lines);

    /**
     * IF-IPP-06：装配作废响应。
     */
    CancelInpatientMedicationDispenseResponse toCancelBatchResponse(List<Long> ids, String message, boolean success);
}
