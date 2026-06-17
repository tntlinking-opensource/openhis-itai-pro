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

package com.openhis.health.application.outpatient.pharmacy.dispense.mapper;

import com.openhis.health.application.outpatient.pharmacy.dispense.dto.CancelMedicationDispenseLineSubmitDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.SavePrepareDispenseLineSubmitDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.CancelOutpatientMedicationDispenseResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.DispenseOutpatientMedicationsResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.LoadPharmacyDispensePageInitResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.PageEncounterMedicationDispenseSlipsResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.PagePendingDispenseEncounterQueueResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.SavePrepareOutpatientMedicationDispenseResponse;
import com.openhis.health.module.querysupport.pharmacy.dto.MedicationDispenseSlipPageResult;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.DispenseMedicationCommand;
import com.openhis.health.domain.medication.medicationdispense.command.PrepareMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 门诊发药工作站装配器：请求/读模型/领域命令/响应之间的纯字段映射。
 *
 * <p>禁止承载领域不变量与业务流程分支。</p>
 */
public interface OutpatientPharmacyDispenseMapper {

    /**
     * IF-OPP-01：装配初始化响应。
     *
     * @param pharmacies                  可操作药房位置列表（adm_location）
     * @param dispenseStatusOptions       发放状态下拉
     * @param encounterClassOptions       就诊类别下拉
     * @param notPerformedReasonOptions   未发药原因下拉（可为空）
     * @param preparerPage                药师分页实体页
     * @return 初始化响应
     */
    LoadPharmacyDispensePageInitResponse toLoadInitResponse(
        List<Location> pharmacies,
        List<OptionDto> dispenseStatusOptions,
        List<OptionDto> encounterClassOptions,
        List<OptionDto> notPerformedReasonOptions,
        Page<Practitioner> preparerPage);

    /**
     * IF-OPP-02：装配就诊队列分页响应。
     *
     * @param encounterIdPage               就诊主键分页壳
     * @param infos                         与队列顺序一致的门诊信息读模型
     * @param dispensePendingSummaryByEncounterId 待发摘要（可为空 Map）
     * @return 队列响应
     */
    PagePendingDispenseEncounterQueueResponse toQueuePage(
        Page<Long> encounterIdPage,
        List<OutpatientEncounterContextQueryModel> infos,
        Map<Long, String> dispensePendingSummaryByEncounterId);

    /**
     * IF-OPP-03：装配药品发药单分页响应。
     *
     * @param slipPage                         发药单读模型分页
     * @param reqByMedReqId                    医嘱列表模型映射
     * @param productByMedicationId            药品定义读模型（按 medicationId）；行内 {@code manufacturerName} 直取 {@code manufacturer} 文本，无额外解析
     * @param inventoryDetailListByDispenseId  候选库存明细（按发放单）
     * @param tcmOnly                          {@code true} 仅中药饮片行
     * @param westernOnly                      {@code true} 仅西药中成药行
     * @param dispensingPractitionerNameByDispenseId 发药人姓名（按发放单）
     * @param encounterBusNo                   就诊业务号
     * @param orderingDepartmentNameByOrgId      开立科室名称（按科室机构 id）
     * @param orderingPractitionerNameById       开立医师姓名（按医务主键）
     * @param dispenseLocationNameByLocationId   发放库房名称（按库位 id）
     * @param chargeUnitPriceByDispenseId        收费明细折算单价（按发放单）
     * @param chargeTotalPriceByDispenseId       收费明细金额合计（按发放单）
     * @param traceNoEffectiveByDispenseId       追溯码（读模型与领域实体合并后的有效值）
     * @param rateCodeLabelMap                   用药频次码→展示
     * @param methodCodeLabelMap                 给药途径码→展示
     * @param doseUnitLabelMap                   剂量单位码→展示
     * @param businessUnitLabelMap               发放/医嘱单位码→展示
     * @param rxFlagLabelMap                     处方类别码→展示
     * @param partAttributeLabelMap              拆分属性码→展示
     * @return 分页响应
     */
    PageEncounterMedicationDispenseSlipsResponse toSlipPage(
        Page<MedicationDispenseSlipQueryModel> slipPage,
        Map<Long, MedicationRequestListQueryModel> reqByMedReqId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
        Map<Long, List<com.openhis.health.application.outpatient.pharmacy.dispense.dto.MedicationDispenseInventoryDetailDto>> inventoryDetailListByDispenseId,
        Boolean tcmOnly,
        Boolean westernOnly,
        Map<Long, String> dispensingPractitionerNameByDispenseId,
        String encounterBusNo,
        Map<Long, String> orderingDepartmentNameByOrgId,
        Map<Long, String> orderingPractitionerNameById,
        Map<Long, String> dispenseLocationNameByLocationId,
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId,
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId,
        Map<Long, String> traceNoEffectiveByDispenseId,
        Map<String, String> rateCodeLabelMap,
        Map<String, String> methodCodeLabelMap,
        Map<String, String> tcmMethodCodeLabelMap,
        Map<String, String> doseUnitLabelMap,
        Map<String, String> businessUnitLabelMap,
        Map<String, String> rxFlagLabelMap,
        Map<String, String> partAttributeLabelMap);

    /**
     * 将 querysupport slip 分页结果包装为 IF-OPP-03 响应。
     */
    PageEncounterMedicationDispenseSlipsResponse toSlipPageResponse(MedicationDispenseSlipPageResult result);

    /**
     * IF-OPP-04：装配保存配药命令。
     *
     * @param operatorId   操作人用户 id（租户登录用户）
     * @param preparerId   调配药师主键
     * @param lines        提交行
     * @return 领域命令
     */
    PrepareMedicationDispenseCommand toPrepareMedicationDispenseCommand(
        Long operatorId,
        Long preparerId,
        List<SavePrepareDispenseLineSubmitDto> lines);

    /**
     * IF-OPP-04：装配保存配药响应。
     *
     * @param ids     已处理发放单 id
     * @param message 提示
     * @param success 是否成功
     * @return 响应
     */
    SavePrepareOutpatientMedicationDispenseResponse toPrepareBatchResponse(List<Long> ids, String message, boolean success);

    /**
     * IF-OPP-05：装配核发命令。
     *
     * @param operatorId      操作人用户 id
     * @param practitionerId  发药药师主键
     * @param dispensers      已校验的发放聚合行（顺序与提交行一致）
     * @return 领域命令
     */
    DispenseMedicationCommand toDispenseMedicationCommand(Long operatorId, Long practitionerId, List<MedicationDispense> dispensers);

    /**
     * IF-OPP-05：装配核发响应。
     *
     * @param ids     已处理 id
     * @param message 提示
     * @param success 是否成功
     * @return 响应
     */
    DispenseOutpatientMedicationsResponse toDispenseBatchResponse(List<Long> ids, String message, boolean success);

    /**
     * IF-OPP-06：装配作废命令。
     *
     * @param operatorId 操作人用户 id
     * @param lines      提交行
     * @return 领域命令
     */
    CancelMedicationDispenseCommand toCancelMedicationDispenseCommand(Long operatorId, List<CancelMedicationDispenseLineSubmitDto> lines);

    /**
     * IF-OPP-06：装配作废响应。
     *
     * @param ids     已处理 id
     * @param message 提示
     * @param success 是否成功
     * @return 响应
     */
    CancelOutpatientMedicationDispenseResponse toCancelBatchResponse(List<Long> ids, String message, boolean success);
}
