package com.openhis.health.module.orchestration.nursestation;

import com.openhis.health.domain.custom.frequency.queries.model.FrequencyExecutionTimePointQueryModel;
import com.openhis.health.domain.custom.frequency.service.FrequencyQueryService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.module.common.assembler.advice.InpatientMedicationRequestExecutionMeasureSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 长期药嘱自然月累计发放上限 Gate（GAP-EXE-010 / BR-IP-EXE-016 补充）。
 *
 * <p>参考上限 = 单次执行发药量 × 自然月内频次展开可执行点数（停嘱截断）。</p>
 */
@Component
@RequiredArgsConstructor
public class LongTermMedicationMonthlyDispenseCapSupport {

    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final FrequencyQueryService frequencyQueryService;

    /**
     * 校验本次执行后自然月累计发放量不超过参考上限。
     *
     * @param medicationRequest 药品医嘱明细
     * @param planTime          计划执行时刻
     * @param incomingQuantity  本次执行拟新建发放数量合计（FIFO 分段之和）
     */
    public void assertMonthlyDispenseWithinCap(
        MedicationRequestListQueryModel medicationRequest,
        LocalDateTime planTime,
        BigDecimal incomingQuantity) {
        if (medicationRequest == null || planTime == null) {
            return;
        }
        if (incomingQuantity == null || incomingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String rateCode = medicationRequest.getRateCode();
        if (StringUtils.isBlank(rateCode)) {
            return;
        }
        BigDecimal perExecutionQuantity = InpatientMedicationRequestExecutionMeasureSupport.resolveDispenseQuantityFromRequest(
            medicationRequest.getQuantity());
        LocalDateTime monthStart = planTime.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = this.resolveNaturalMonthEnd(planTime);
        LocalDateTime expandStart = monthStart;
        if (medicationRequest.getReqAuthoredTime() != null) {
            LocalDateTime authoredDayStart = medicationRequest.getReqAuthoredTime().toLocalDate().atStartOfDay();
            if (authoredDayStart.isAfter(expandStart)) {
                expandStart = authoredDayStart;
            }
        }
        LocalDateTime expandEnd = monthEnd;
        if (RequestStatus.STOPPED.getCode().equals(medicationRequest.getStatusEnum())
            && medicationRequest.getEffectiveDoseEnd() != null
            && medicationRequest.getEffectiveDoseEnd().isBefore(expandEnd)) {
            expandEnd = medicationRequest.getEffectiveDoseEnd();
        }
        if (!expandEnd.isAfter(expandStart)) {
            throw this.monthlyCapExceededException();
        }
        List<FrequencyExecutionTimePointQueryModel> points =
            frequencyQueryService.computeExecutionTimePoints(rateCode, expandStart, expandEnd);
        int executablePointCount = points == null ? 0 : points.size();
        if (executablePointCount <= 0) {
            throw this.monthlyCapExceededException();
        }
        BigDecimal monthlyCap = perExecutionQuantity.multiply(BigDecimal.valueOf(executablePointCount));
        BigDecimal alreadyDispensed = medicationDispenseQueryService.sumActiveDispenseQuantityByMedReqIdInNaturalMonth(
            medicationRequest.getId(),
            planTime);
        if (alreadyDispensed.add(incomingQuantity).compareTo(monthlyCap) > 0) {
            throw this.monthlyCapExceededException();
        }
    }

    private LocalDateTime resolveNaturalMonthEnd(LocalDateTime anchorTime) {
        LocalDate monthDate = anchorTime.toLocalDate();
        return monthDate.withDayOfMonth(monthDate.lengthOfMonth()).atTime(23, 59, 59);
    }

    private BusinessException monthlyCapExceededException() {
        return new BusinessException(
            InpatientNurseStationConstants.EX_IP_EXE_305,
            "长期医嘱本月累计发药量已达上限，请核对频次与停嘱或下月再执行");
    }
}
