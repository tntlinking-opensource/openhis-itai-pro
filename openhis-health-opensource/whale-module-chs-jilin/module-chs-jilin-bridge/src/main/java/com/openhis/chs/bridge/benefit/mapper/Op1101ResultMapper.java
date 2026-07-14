/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.benefit.mapper;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.outpatient.command.YbIdetInfoRow;
import com.openhis.chs.domain.outpatient.command.YbInsuInfoRow;
import com.openhis.chs.domain.outpatient.result.YbPersonInfoResult;
import com.openhis.chs.domain.outpatient.entity.YbPersonCache;
import com.openhis.chs.gateway.scheme.common.model.ChsSchemeJsonSupport;
import com.openhis.chs.gateway.scheme.benefit.model.Op1101IdetInfoRow;
import com.openhis.chs.gateway.scheme.benefit.model.Op1101InsuInfoRow;
import com.openhis.chs.gateway.scheme.benefit.model.Op1101Output;

/** Op1101Output / 人员缓存 → domain {@link YbPersonInfoResult} 静态映射（无 IO、无 Spring 依赖）。 */
public final class Op1101ResultMapper {

    private Op1101ResultMapper() {
    }

    /** 从当日有效人员缓存行还原查询结果；优先解析 snapshotJson 完整输出。 */
    public static YbPersonInfoResult fromCache(YbPersonCache row) {
        if (isNotEmpty(row.getSnapshotJson())) {
            try {
                Op1101Output output = ChsSchemeJsonSupport.parseOutputString(
                    row.getSnapshotJson().trim(), Op1101Output.class);
                if (output != null && output.getBaseinfo() != null) {
                    return fromOutput(output, row.getInsuplcAdmdvs());
                }
            } catch (RuntimeException ignored) {
                // fall through to scalar fields
            }
        }
        YbPersonInfoResult result = new YbPersonInfoResult();
        result.setPsnNo(row.getPsnNo());
        result.setInsuplcAdmdvs(row.getInsuplcAdmdvs());
        result.setInsutype(row.getInsutype());
        result.setBalc(row.getBalc());
        return result;
    }

    /** 将 1101 平台输出映射为 domain 结果，并按 preferredAdmdvs 选取默认险种行。 */
    public static YbPersonInfoResult fromOutput(Op1101Output output, String preferredAdmdvs) {
        YbPersonInfoResult result = new YbPersonInfoResult();
        result.setPsnNo(output.getBaseinfo().getPsnNo());
        result.setPsnName(output.getBaseinfo().getPsnName());
        result.setCertno(output.getBaseinfo().getCertno());
        result.setGend(output.getBaseinfo().getGend());
        result.setBrdy(output.getBaseinfo().getBrdy());
        if (output.getInsuinfo() != null) {
            for (Op1101InsuInfoRow item : output.getInsuinfo()) {
                if (item == null) {
                    continue;
                }
                result.getInsuinfoList().add(mapInsuInfoRow(item));
            }
        }
        if (output.getIdetinfo() != null) {
            for (Op1101IdetInfoRow item : output.getIdetinfo()) {
                if (item == null) {
                    continue;
                }
                result.getIdetinfoList().add(mapIdetInfoRow(item));
            }
        }
        if (!result.getInsuinfoList().isEmpty()) {
            YbInsuInfoRow picked = result.getInsuinfoList().get(0);
            if (isNotEmpty(preferredAdmdvs)) {
                for (YbInsuInfoRow row : result.getInsuinfoList()) {
                    if (preferredAdmdvs.equals(row.getInsuplcAdmdvs())) {
                        picked = row;
                        break;
                    }
                }
            }
            result.setInsuplcAdmdvs(picked.getInsuplcAdmdvs());
            result.setInsutype(picked.getInsutype());
            result.setBalc(picked.getBalc());
        }
        return result;
    }

    /**
     * 将 1101 平台险种信息行映射为 domain 险种行。
     *
     * @param item 1101 平台险种信息行
     * @return domain 险种信息行
     */
    private static YbInsuInfoRow mapInsuInfoRow(Op1101InsuInfoRow item) {
        YbInsuInfoRow row = new YbInsuInfoRow();
        row.setInsuplcAdmdvs(item.getInsuplcAdmdvs());
        row.setInsutype(item.getInsutype());
        row.setPsnType(item.getPsnType());
        row.setPsnInsuStas(item.getPsnInsuStas());
        row.setPsnInsuDate(item.getPsnInsuDate());
        row.setPausInsuDate(item.getPausInsuDate());
        row.setCvlservFlag(item.getCvlservFlag());
        row.setEmpName(item.getEmpName());
        row.setBalc(item.getBalc());
        return row;
    }

    /**
     * 将 1101 平台身份信息行映射为 domain 身份行。
     *
     * @param item 1101 平台身份信息行
     * @return domain 身份信息行
     */
    private static YbIdetInfoRow mapIdetInfoRow(Op1101IdetInfoRow item) {
        YbIdetInfoRow row = new YbIdetInfoRow();
        row.setPsnIdetType(item.getPsnIdetType());
        row.setPsnTypeLv(item.getPsnTypeLv());
        row.setMemo(item.getMemo());
        row.setBegntime(item.getBegntime());
        row.setEndtime(item.getEndtime());
        return row;
    }
}
