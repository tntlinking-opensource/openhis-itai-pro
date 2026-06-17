/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.skintest.assembler;

import com.openhis.health.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestEnumOptionDto;
import com.openhis.health.application.inpatient.nursestation.skintest.response.LoadNurseStationSkinTestInitResponse;
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
