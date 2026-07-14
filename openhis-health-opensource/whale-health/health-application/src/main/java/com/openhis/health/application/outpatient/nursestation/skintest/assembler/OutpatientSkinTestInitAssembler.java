/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.skintest.assembler;

import com.openhis.health.application.outpatient.nursestation.skintest.dto.OutpatientSkinTestEnumOptionDto;
import com.openhis.health.application.outpatient.nursestation.skintest.response.LoadOutpatientSkinTestInitResponse;
import com.openhis.health.domain.share.enums.AllergyCriticality;
import com.openhis.health.domain.share.enums.AllergySeverity;
import com.openhis.health.domain.share.enums.AllergyVerificationStatus;
import com.openhis.health.domain.share.enums.ClinicalStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-OP-STK-01 皮试表单枚举装配。
 */
@Component
public class OutpatientSkinTestInitAssembler {

    /**
     * 装载皮试表单四类枚举下拉。
     *
     * @return 初始化响应
     */
    public LoadOutpatientSkinTestInitResponse assembleInitOptions() {
        LoadOutpatientSkinTestInitResponse response = new LoadOutpatientSkinTestInitResponse();
        response.setClinicalStatusOptions(this.toSkinTestOptions(EnumUtil.toOptions(ClinicalStatus.class)));
        response.setVerificationStatusOptions(this.toSkinTestOptions(EnumUtil.toOptions(AllergyVerificationStatus.class)));
        response.setCriticalityOptions(this.toSkinTestOptions(EnumUtil.toOptions(AllergyCriticality.class)));
        response.setSeverityOptions(this.toSkinTestOptions(EnumUtil.toOptions(AllergySeverity.class)));
        return response;
    }

    /**
     * 通用 OptionDto → 皮试枚举选项 DTO（code + displayText）。
     *
     * @param source 源选项
     * @return 皮试选项列表
     */
    private List<OutpatientSkinTestEnumOptionDto> toSkinTestOptions(List<OptionDto> source) {
        List<OutpatientSkinTestEnumOptionDto> target = new ArrayList<>();
        if (source == null) {
            return target;
        }
        for (OptionDto option : source) {
            if (option == null) {
                continue;
            }
            OutpatientSkinTestEnumOptionDto row = new OutpatientSkinTestEnumOptionDto();
            row.setCode(option.getValue());
            row.setDisplayText(option.getLabel());
            target.add(row);
        }
        return target;
    }
}
