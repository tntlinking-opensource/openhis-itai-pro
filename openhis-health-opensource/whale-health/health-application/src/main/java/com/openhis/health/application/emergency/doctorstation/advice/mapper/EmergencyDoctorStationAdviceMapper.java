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

package com.openhis.health.application.emergency.doctorstation.advice.mapper;

import com.openhis.health.application.common.assembler.advice.ServiceAdviceDraftCommandSupport;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.SaveServiceAdviceDraftLinePayload;
import com.openhis.health.application.outpatient.doctorstation.advice.mapper.DoctorStationAdviceMapper;
import com.openhis.health.application.outpatient.doctorstation.advice.request.SaveDoctorStationAdviceDraftBatchRequest;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.command.VoidUnbilledDraftChargeItemsByServiceAnchorCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.medication.medicationrequest.command.AdjustMedicationOrderGroupCommand;
import com.openhis.health.domain.medication.medicationrequest.command.ChangeMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.CreateMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.RemoveMedicationOrderDraftCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SignMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SignOffMedicationOrderCommand;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionBaseQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel;
import com.openhis.health.domain.workflow.devicerequest.command.ChangeDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.command.CreateDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.command.RemoveDeviceOrderDraftCommand;
import com.openhis.health.domain.workflow.devicerequest.command.SignDeviceOrderCommand;
import com.openhis.health.domain.workflow.devicerequest.command.SignOffDeviceOrderCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.ModifyServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RemoveServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.SignServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.WithdrawSignedServiceRequestCommand;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 急诊医嘱：包装门诊 {@link DoctorStationAdviceMapper}，诊疗开立使用活动定义详情模型；生成来源与门诊一致为医生开立。
 */
@Component
@RequiredArgsConstructor
public class EmergencyDoctorStationAdviceMapper {

    private final DoctorStationAdviceMapper delegate;

    public void validatePatientEncounter(Long patientId, Long encounterPatientId) {
        delegate.validatePatientEncounter(patientId, encounterPatientId);
    }

    public CreateMedicationOrderDraftCommand toCreateMedicationOrderDraftCommand(
        SaveDoctorStationAdviceDraftBatchRequest parent,
        DoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId
    ) {
        CreateMedicationOrderDraftCommand cmd = delegate.toCreateMedicationOrderDraftCommand(parent, line, operatorId, orgId);
        cmd.setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        return cmd;
    }

    public ChangeMedicationOrderDraftCommand toChangeMedicationOrderDraftCommand(DoctorStationAdviceDraftLineRequest line, Long operatorId) {
        return delegate.toChangeMedicationOrderDraftCommand(line, operatorId);
    }

    public RemoveMedicationOrderDraftCommand toRemoveMedicationOrderDraftCommand(Long requestId, Long operatorId) {
        return delegate.toRemoveMedicationOrderDraftCommand(requestId, operatorId);
    }

    public CreateDeviceOrderDraftCommand toCreateDeviceOrderDraftCommand(
        SaveDoctorStationAdviceDraftBatchRequest parent,
        DoctorStationAdviceDraftLineRequest line,
        Long operatorId
    ) {
        CreateDeviceOrderDraftCommand cmd = delegate.toCreateDeviceOrderDraftCommand(parent, line, operatorId);
        if (cmd.getItem() != null) {
            cmd.getItem().setGenerateSourceEnum(GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        }
        return cmd;
    }

    public ChangeDeviceOrderDraftCommand toChangeDeviceOrderDraftCommand(DoctorStationAdviceDraftLineRequest line, Long operatorId) {
        return delegate.toChangeDeviceOrderDraftCommand(line, operatorId);
    }

    public RemoveDeviceOrderDraftCommand toRemoveDeviceOrderDraftCommand(Long requestId, Long operatorId) {
        return delegate.toRemoveDeviceOrderDraftCommand(requestId, operatorId);
    }

    public CreateServiceRequestDraftCommand toCreateServiceRequestDraftCommand(
        SaveDoctorStationAdviceDraftBatchRequest parent,
        DoctorStationAdviceDraftLineRequest line,
        ProductDefinitionDisplayDto productRm,
        Long operatorId
    ) {
        return delegate.toCreateServiceRequestDraftCommand(parent, line, productRm, operatorId);
    }

    public ModifyServiceRequestDraftCommand toModifyServiceRequestDraftCommand(DoctorStationAdviceDraftLineRequest line, Long operatorId) {
        return delegate.toModifyServiceRequestDraftCommand(line, operatorId);
    }

    public RemoveServiceRequestDraftCommand toRemoveServiceRequestDraftCommand(Long requestId, Long operatorId) {
        return delegate.toRemoveServiceRequestDraftCommand(requestId, operatorId);
    }

    public VoidUnbilledDraftChargeItemsByServiceAnchorCommand toVoidDraftChargeAnchor(
        Long encounterId,
        String serviceTable,
        Long serviceId,
        Long operatorId
    ) {
        return delegate.toVoidDraftChargeAnchor(encounterId, serviceTable, serviceId, operatorId);
    }

    public CreateChargeItemCommand toCreateChargeItemDraftFromOrder(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long requestId,
        String serviceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        ChargeItemDefinition priceLine,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow
    ) {
        return delegate.toCreateChargeItemDraftFromOrder(
            encounterId, patientId, accountId, orgId, operatorId, requestId, serviceTable,
            productDefinitionTable, productDefinitionId, priceLine, quantity, quantityUnit, productRow);
    }

    public CreateChargeItemCommand.Item toCreateChargeItemDraftItemFromOrder(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long serviceId,
        String serviceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        ChargeItemDefinition priceLine,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow
    ) {
        return delegate.toCreateChargeItemDraftItemFromOrder(
            encounterId, patientId, accountId, orgId, serviceId, serviceTable,
            productDefinitionTable, productDefinitionId, priceLine, quantity, quantityUnit, productRow);
    }

    public CreateChargeItemCommand.Item toCreateChargeItemDraftItemForFifoDispenseSegment(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long requestId,
        String requestServiceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        String dispenseTable,
        Long dispenseId,
        ChargeItemDefinition priceLine,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow
    ) {
        return delegate.toCreateChargeItemDraftItemForFifoDispenseSegment(
            encounterId,
            patientId,
            accountId,
            orgId,
            requestId,
            requestServiceTable,
            productDefinitionTable,
            productDefinitionId,
            dispenseTable,
            dispenseId,
            priceLine,
            quantity,
            quantityUnit,
            productRow);
    }

    public SignMedicationOrderCommand toSignMedicationOrderCommand(Long id, String signCode, String prescriptionNo, Long operatorId) {
        return delegate.toSignMedicationOrderCommand(id, signCode, prescriptionNo, operatorId);
    }

    public SignOffMedicationOrderCommand toSignOffMedicationOrderCommand(Long id, Long operatorId) {
        return delegate.toSignOffMedicationOrderCommand(id, operatorId);
    }

    public SignServiceRequestCommand toSignServiceRequestCommand(Long id, String signCode, Long operatorId) {
        return delegate.toSignServiceRequestCommand(id, signCode, operatorId);
    }

    public WithdrawSignedServiceRequestCommand toWithdrawServiceRequestCommand(Long id, Long operatorId) {
        return delegate.toWithdrawServiceRequestCommand(id, operatorId);
    }

    public SignDeviceOrderCommand toSignDeviceOrderCommand(Long id, Long operatorId) {
        return delegate.toSignDeviceOrderCommand(id, operatorId);
    }

    public SignOffDeviceOrderCommand toSignOffDeviceOrderCommand(Long id, Long operatorId) {
        return delegate.toSignOffDeviceOrderCommand(id, operatorId);
    }

    public AdjustMedicationOrderGroupCommand toAdjustMedicationGroupCommand(List<Long> ids, Long groupId, Long operatorId) {
        return delegate.toAdjustMedicationGroupCommand(ids, groupId, operatorId);
    }

    public Long parseOrderBindItemId(String itemNo, Long itemId) {
        return delegate.parseOrderBindItemId(itemNo, itemId);
    }
}
