/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.support;

import com.openhis.health.application.peis.group.dto.ContractRowDto;
import com.openhis.health.application.peis.group.dto.ContractTypeRowDto;
import com.openhis.health.application.peis.group.request.ListValidContractsForPatientRequest;
import com.openhis.health.application.peis.group.service.PeisGroupCheckAdminAppService;
import com.openhis.health.domain.share.extension.peis.PeisContractPatientPort;
import com.openhis.health.domain.share.extension.peis.PeisContractTypeView;
import com.openhis.health.domain.share.extension.peis.PeisValidContractView;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 患者合同只读 Port：委托 peis 团检 AdminAppService。
 */
@Component
@RequiredArgsConstructor
public class PeisContractPatientPortImpl implements PeisContractPatientPort {

    private final PeisGroupCheckAdminAppService peisGroupCheckAdminAppService;

    /**
     * 列出已启用合同类型字典。
     *
     * @return 类型视图列表
     */
    @Override
    public List<PeisContractTypeView> listEnabledContractTypes() {
        // Pre：读 peis 启用字典
        List<PeisContractTypeView> views = new ArrayList<>();
        for (ContractTypeRowDto row : peisGroupCheckAdminAppService.listContractTypes()) {
            PeisContractTypeView view = new PeisContractTypeView();
            view.setTypeCode(row.getTypeCode());
            view.setTypeName(row.getTypeName());
            view.setSortOrder(row.getSortOrder());
            views.add(view);
        }
        // Asm：返回跨模块视图
        return views;
    }

    /**
     * 按患者查询当日有效团检合同。
     *
     * @param patientId 患者主键
     * @return 有效合同视图
     */
    @Override
    public List<PeisValidContractView> listValidContractsForPatient(Long patientId) {
        // Gate：患者必填
        if (patientId == null) {
            throw new ValidationException("查询有效合同失败：患者不能为空");
        }
        ListValidContractsForPatientRequest request = new ListValidContractsForPatientRequest();
        request.setPatientId(patientId);
        List<PeisValidContractView> views = new ArrayList<>();
        for (ContractRowDto row : peisGroupCheckAdminAppService.listValidContractsForPatient(request)) {
            PeisValidContractView view = new PeisValidContractView();
            view.setContractId(row.getContractId());
            view.setContractName(row.getContractName());
            view.setContractTypeCode(row.getContractTypeCode());
            view.setContractTypeName(row.getContractTypeName());
            view.setValidFrom(row.getValidFrom());
            view.setValidTo(row.getValidTo());
            views.add(view);
        }
        // Asm：供 miniapp 手选进池
        return views;
    }
}
