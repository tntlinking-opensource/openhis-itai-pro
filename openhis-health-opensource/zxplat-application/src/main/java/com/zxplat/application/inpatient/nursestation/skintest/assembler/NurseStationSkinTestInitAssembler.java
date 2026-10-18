/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.skintest.assembler;

import com.zxplat.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestEnumOptionDto;
import com.zxplat.application.inpatient.nursestation.skintest.response.LoadNurseStationSkinTestInitResponse;
import com.zxplat.domain.share.enums.AllergyCriticality;
import com.zxplat.domain.share.enums.AllergySeverity;
import com.zxplat.domain.share.enums.AllergyVerificationStatus;
import com.zxplat.domain.share.enums.ClinicalStatus;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-STK-01 皮试表单枚举装配。
 */
@Component
public class NurseStationSkinTestInitAssembler {

    /**
     * 装载皮试表单四类枚举下拉。
     *
     * @return 初始化响应
     */
    public LoadNurseStationSkinTestInitResponse assembleInitOptions() {
        LoadNurseStationSkinTestInitResponse response = new LoadNurseStationSkinTestInitResponse();
        response.setClinicalStatusOptions(this.toSkinTestOptions(EnumUtil.toOptions(ClinicalStatus.class)));
        response.setVerificationStatusOptions(this.toSkinTestOptions(EnumUtil.toOptions(AllergyVerificationStatus.class)));
        response.setCriticalityOptions(this.toSkinTestOptions(EnumUtil.toOptions(AllergyCriticality.class)));
        response.setSeverityOptions(this.toSkinTestOptions(EnumUtil.toOptions(AllergySeverity.class)));
        return response;
    }

    /**
     * 框架 OptionDto 转为皮试专用枚举选项。
     *
     * @param source 枚举选项源
     * @return 皮试枚举选项列表
     */
    private List<NurseStationSkinTestEnumOptionDto> toSkinTestOptions(List<OptionDto> source) {
        List<NurseStationSkinTestEnumOptionDto> target = new ArrayList<>();
        if (source == null) {
            return target;
        }
        for (OptionDto option : source) {
            if (option == null) {
                continue;
            }
            NurseStationSkinTestEnumOptionDto row = new NurseStationSkinTestEnumOptionDto();
            row.setCode(option.getValue());
            row.setDisplayText(option.getLabel());
            target.add(row);
        }
        return target;
    }
}
