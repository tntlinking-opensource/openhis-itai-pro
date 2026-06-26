/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.application.prescription.support;

import com.openhis.elep.domain.prescription.entity.YbElepPrescription;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;

/** 续方前置校验（对齐 07 详细设计 §4.4）。 */
public final class ElepPrescriptionContinuationRules {

    private ElepPrescriptionContinuationRules() {
    }

    public static void validate(YbElepPrescription source) {
        if (StringUtils.isBlank(source.getHiRxno())) {
            throw new BusinessException("ELEP_CONTINUATION_DENIED", "源方无 hi_rxno，无法续方，请使用套用");
        }
        if ("1".equals(source.getReptFlag())) {
            int max = source.getMaxReptCnt() == null ? 0 : source.getMaxReptCnt();
            int used = source.getReptdCnt() == null ? 0 : source.getReptdCnt();
            if (used >= max) {
                throw new BusinessException("ELEP_CONTINUATION_DENIED", "续方次数已达上限，请使用套用");
            }
        }
        if (source.getValiEndTime() != null && Instant.now().isAfter(source.getValiEndTime())) {
            throw new BusinessException("ELEP_CONTINUATION_DENIED", "源方已过有效期，请使用套用");
        }
    }

    public static boolean canContinuation(YbElepPrescription rx) {
        if (rx == null || StringUtils.isBlank(rx.getHiRxno())) {
            return false;
        }
        if ("1".equals(rx.getReptFlag())) {
            int max = rx.getMaxReptCnt() == null ? 0 : rx.getMaxReptCnt();
            int used = rx.getReptdCnt() == null ? 0 : rx.getReptdCnt();
            if (used >= max) {
                return false;
            }
        }
        return rx.getValiEndTime() == null || !Instant.now().isAfter(rx.getValiEndTime());
    }
}
