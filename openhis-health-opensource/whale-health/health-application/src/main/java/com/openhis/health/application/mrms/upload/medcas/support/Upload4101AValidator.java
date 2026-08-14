/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.support;

import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListDiseInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListOprnInfo;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListPayload;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListSetlinfo;
import com.openhis.whale.core.application.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 4101A 组装校验（规范必填 + 联系人条件必填 + 护理四天 Gate）。
 */
public final class Upload4101AValidator {

    /**
     * 禁止实例化：仅静态校验方法。
     */
    private Upload4101AValidator() {
    }

    /**
     * 校验 4101A 载荷。
     *
     * @param input 4101A 载荷
     */
    public static void validate(MedcasSettleListPayload input) {
        List<String> errors = new ArrayList<>();
        if (input == null || input.getSetlinfo() == null) {
            throw new BusinessException(MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION, "4101A setlinfo 不能为空");
        }
        MedcasSettleListSetlinfo s = input.getSetlinfo();
        require(errors, "psn_no", s.getPsnNo());
        require(errors, "mdtrt_id", s.getMdtrtId());
        require(errors, "setl_id", s.getSetlId());
        require(errors, "ntly", s.getNtly());
        require(errors, "prfs", s.getPrfs());
        require(errors, "dscg_caty", s.getDscgCaty());
        require(errors, "bill_code", s.getBillCode());
        require(errors, "bill_no", s.getBillNo());
        require(errors, "biz_sn", s.getBizSn());
        require(errors, "setl_begn_date", s.getSetlBegnDate());
        require(errors, "setl_end_date", s.getSetlEndDate());
        require(errors, "medins_fill_dept", s.getMedinsFillDept());
        require(errors, "medins_fill_psn", s.getMedinsFillPsn());
        // 有联系人姓名则关系/地址/电话必填
        if (trimToNull(s.getConerName()) != null) {
            require(errors, "patn_rlts", s.getPatnRlts());
            require(errors, "coner_addr", s.getConerAddr());
            require(errors, "coner_tel", s.getConerTel());
        }
        // 老结算上传 Gate：护理四天须可解析
        if (!isNumericDay(s.getSpgaNurscareDays())
                || !isNumericDay(s.getLv1NurscareDays())
                || !isNumericDay(s.getScdNurscareDays())
                || !isNumericDay(s.getLv3NurscareDays())) {
            errors.add("病案首页住院信息中护理天数未填写完全");
        }
        if (input.getDiseinfo() != null) {
            for (MedcasSettleListDiseInfo row : input.getDiseinfo()) {
                if (trimToNull(row.getDiagCode()) == null
                        || trimToNull(row.getDiagName()) == null
                        || trimToNull(row.getDiagType()) == null
                        || trimToNull(row.getMaindiagFlag()) == null
                        || trimToNull(row.getAdmCondType()) == null) {
                    errors.add("结算清单诊断行缺必填字段");
                    break;
                }
            }
        }
        if (input.getOprninfo() != null) {
            for (MedcasSettleListOprnInfo row : input.getOprninfo()) {
                if (trimToNull(row.getOprnOprtCode()) == null
                        || trimToNull(row.getOprnOprtName()) == null
                        || trimToNull(row.getOprnOprtType()) == null
                        || trimToNull(row.getAnstWay()) == null
                        || trimToNull(row.getOperDrCode()) == null) {
                    errors.add("结算清单手术行缺必填字段（含麻醉方式/术者医师代码）");
                    break;
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(
                    MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION,
                    String.join("；", errors));
        }
    }

    /**
     * 非空校验。
     *
     * @param errors 错误列表
     * @param label 字段名
     * @param value 值
     */
    private static void require(List<String> errors, String label, String value) {
        if (trimToNull(value) == null) {
            errors.add(label + " 缺失");
        }
    }

    /**
     * 护理天数是否可解析为数字（含 0）。
     *
     * @param raw 文本
     * @return true 可解析
     */
    private static boolean isNumericDay(String raw) {
        if (trimToNull(raw) == null) {
            return false;
        }
        try {
            Double.parseDouble(raw.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
