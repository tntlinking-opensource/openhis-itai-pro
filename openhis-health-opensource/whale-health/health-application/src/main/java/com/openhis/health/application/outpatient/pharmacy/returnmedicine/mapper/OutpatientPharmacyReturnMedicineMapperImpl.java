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

import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.dto.DepartmentOptionDto;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.dto.RefundStatusOptionDto;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.dto.ReturnMedicineDetailRowDto;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.dto.ReturnPatientRowDto;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.GetReturnMedicineDetailRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.PageReturnPatientRequest;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.medication.medicationdispense.query.PageReturnPatientQuery;
import com.openhis.health.domain.medication.medicationdispense.query.ReturnMedicineDetailQuery;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicineDetailReadRow;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicinePatientQueueRow;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link OutpatientPharmacyReturnMedicineMapper} 的手写实现：逐字段搬运与展示脱敏。
 */
@Component
public class OutpatientPharmacyReturnMedicineMapperImpl implements OutpatientPharmacyReturnMedicineMapper {

    /**
     * 将分页请求映射为退药患者队列查询。
     *
     * @param request 应用请求；调用方保证非空
     * @return 领域查询入参
     */
    @Override
    public PageReturnPatientQuery toPageReturnPatientQuery(PageReturnPatientRequest request) {
        PageReturnPatientQuery q = new PageReturnPatientQuery();
        PageQueryBinder.bind(request, q);
        if (StringUtils.isBlank(q.getKeyword()) && StringUtils.isNotBlank(request.getSearchKey())) {
            q.setKeyword(request.getSearchKey().trim());
        }
        q.setDepartmentId(request.getDepartmentId());
        return q;
    }

    /**
     * 将明细请求映射为领域查询。
     *
     * @param request 应用请求；调用方保证非空
     * @return 领域查询入参
     */
    @Override
    public ReturnMedicineDetailQuery toReturnMedicineDetailQuery(GetReturnMedicineDetailRequest request) {
        ReturnMedicineDetailQuery q = new ReturnMedicineDetailQuery();
        q.setEncounterId(request.getEncounterId());
        q.setRefundStatus(request.getRefundStatus());
        return q;
    }

    /**
     * 将启用机构行映射为科室下拉选项。
     *
     * @param organizations 机构列表；允许为空
     * @return 选项列表；空入参返回空列表
     */
    @Override
    public List<DepartmentOptionDto> mapToDepartmentOptions(List<Organization> organizations) {
        List<DepartmentOptionDto> list = new ArrayList<>();
        if (organizations == null) {
            return list;
        }
        for (Organization org : organizations) {
            if (org == null || org.getId() == null) {
                continue;
            }
            DepartmentOptionDto dto = new DepartmentOptionDto();
            dto.setDepartmentId(org.getId());
            dto.setDepartmentName(StringUtils.defaultString(org.getName()));
            list.add(dto);
        }
        return list;
    }

    /**
     * 装配退药状态下拉项（固定 PENDING_REFUND/REFUNDED 子集）。
     *
     * @return 非空列表
     */
    @Override
    public List<RefundStatusOptionDto> mapToRefundStatusOptions() {
        List<RefundStatusOptionDto> list = new ArrayList<>();
        RefundStatusOptionDto pending = new RefundStatusOptionDto();
        pending.setStatusCode(DispenseStatus.PENDING_REFUND.getCode());
        pending.setStatusName(DispenseStatus.PENDING_REFUND.getDisplay());
        list.add(pending);
        RefundStatusOptionDto refunded = new RefundStatusOptionDto();
        refunded.setStatusCode(DispenseStatus.REFUNDED.getCode());
        refunded.setStatusName(DispenseStatus.REFUNDED.getDisplay());
        list.add(refunded);
        return list;
    }

    /**
     * 将退药患者队列读行映射为对外 DTO 列表。
     *
     * @param rows 读侧装配行；允许为空
     * @return DTO 列表；空入参返回空列表
     */
    @Override
    public List<ReturnPatientRowDto> mapToReturnPatientRows(List<ReturnMedicinePatientQueueRow> rows) {
        List<ReturnPatientRowDto> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (ReturnMedicinePatientQueueRow row : rows) {
            if (row == null) {
                continue;
            }
            ReturnPatientRowDto dto = new ReturnPatientRowDto();
            dto.setEncounterId(row.getEncounterId());
            dto.setPatientName(row.getPatientName());
            dto.setGenderEnum(row.getGenderEnum());
            if (StringUtils.isNotBlank(row.getGenderEnumText())) {
                dto.setGenderEnumText(row.getGenderEnumText());
            } else {
                dto.setGenderEnumText(this.toGenderText(row.getGenderEnum()));
            }
            dto.setAge(StringUtils.defaultString(row.getAge()));
            dto.setIdCard(this.maskIdCard(row.getIdCardRaw()));
            dto.setDepartmentName(StringUtils.defaultString(row.getDepartmentName()));
            dto.setRefundEnum(row.getRefundEnum());
            dto.setRefundEnumText(row.getRefundEnumText());
            dto.setEncounterNo(StringUtils.defaultString(row.getEncounterNo()));
            dto.setReceptionTime(StringUtils.defaultString(row.getReceptionTime()));
            list.add(dto);
        }
        return list;
    }

    /**
     * 将退药明细读行映射为对外 DTO 列表。
     *
     * @param rows 读侧装配行；允许为空
     * @return DTO 列表；空入参返回空列表
     */
    @Override
    public List<ReturnMedicineDetailRowDto> mapToReturnMedicineDetailRows(List<ReturnMedicineDetailReadRow> rows) {
        List<ReturnMedicineDetailRowDto> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (ReturnMedicineDetailReadRow row : rows) {
            if (row == null) {
                continue;
            }
            ReturnMedicineDetailRowDto dto = new ReturnMedicineDetailRowDto();
            dto.setDispenseId(row.getDispenseId());
            dto.setRequestId(row.getRequestId());
            dto.setRefundMedicineId(row.getRefundOriginRequestId());
            dto.setItemId(row.getItemId());
            dto.setItemName(row.getItemName());
            dto.setLotNumber(row.getLotNumber());
            dto.setQuantity(row.getQuantity());
            dto.setUnitCode(row.getUnitCode());
            dto.setUnitCodeText(row.getUnitCodeText());
            dto.setTotalPrice(row.getTotalPrice());
            dto.setDoctorName(row.getDoctorName());
            dto.setRefundEnum(row.getRefundEnum());
            dto.setRefundEnumText(row.getRefundEnumText());
            dto.setReqStatus(row.getReqStatus());
            dto.setReqStatusText(row.getReqStatusText());
            dto.setServiceTable(row.getServiceTable());
            dto.setTraceNo(row.getTraceNo());
            list.add(dto);
        }
        return list;
    }

    /**
     * 性别编码转展示名。
     *
     * @param genderEnum 编码
     * @return 展示名
     */
    private String toGenderText(String genderEnum) {
        AdministrativeGender g = EnumUtil.fromCode(AdministrativeGender.class, genderEnum);
        if (g == null) {
            if (genderEnum == null) {
                return GeneralSharedConstants.EMPTY_STRING;
            }
            return genderEnum;
        }
        return g.getDisplay();
    }

    /**
     * 证件号脱敏：保留前三后四，中间星号填充。
     *
     * @param raw 原始证件号
     * @return 脱敏串；空入参返回空串
     */
    private String maskIdCard(String raw) {
        if (StringUtils.isBlank(raw)) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String t = raw.trim();
        if (t.length() <= 7) {
            return "***";
        }
        String prefix = t.substring(0, 3);
        String suffix = t.substring(t.length() - 4);
        return prefix + "***********" + suffix;
    }
}
