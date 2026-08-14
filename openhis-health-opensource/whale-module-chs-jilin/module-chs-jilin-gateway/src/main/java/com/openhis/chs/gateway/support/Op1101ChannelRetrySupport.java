/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.support;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.chs.domain.share.constants.ChsPlatformCodes;
import com.openhis.chs.domain.share.support.ChsTextSupport;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.config.ContractConfig;
import com.openhis.chs.gateway.config.ContractConfigLoader;

/**
 * 1101 异地就医地区划失败时，判断是否应切换省通道（adm_vs=229900）重试。
 */
public final class Op1101ChannelRetrySupport {

    /** 吉林省医保（开通异地服务）就医地区划。 */
    public static final String PROVINCIAL_MDTRT_ADMVS = "229900";

    private Op1101ChannelRetrySupport() {
    }

    /**
     * 是否应按省通道重试：平台失败且文案指向异地就医地区划，且当前合同尚未是省通道。
     *
     * @param body          平台响应
     * @param contractBusNo 当前合同 bus_no
     * @return true 时应查找省通道并重试一次
     */
    public static boolean shouldRetryWithProvincialChannel(ResultBody body, String contractBusNo) {
        if (body == null || ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode())) {
            return false;
        }
        if (isEmpty(contractBusNo)) {
            return false;
        }
        ContractConfig current = ContractConfigLoader.getByBusNo(contractBusNo.trim());
        if (current == null) {
            return false;
        }
        if (PROVINCIAL_MDTRT_ADMVS.equals(trimToEmpty(current.getAdmVs()))) {
            return false;
        }
        String err = ChsTextSupport.platformErrMsg(body, "");
        return isOffsiteTreatmentAreaError(err);
    }

    /**
     * 解析同机构省通道契约；当前已是省通道或找不到时返回 null（调用方勿重试）。
     *
     * @param contractBusNo 当前合同 bus_no
     * @return 省通道配置；无需切换时 null
     */
    public static ContractConfig resolveProvincialSibling(String contractBusNo) {
        if (isEmpty(contractBusNo)) {
            return null;
        }
        ContractConfig provincial = ContractConfigLoader.findSiblingByAdmVs(
            contractBusNo.trim(), PROVINCIAL_MDTRT_ADMVS);
        if (provincial == null || isEmpty(provincial.getBusNo())) {
            return null;
        }
        if (provincial.getBusNo().trim().equals(contractBusNo.trim())) {
            return null;
        }
        if (!PROVINCIAL_MDTRT_ADMVS.equals(trimToEmpty(provincial.getAdmVs()))) {
            return null;
        }
        return provincial;
    }

    /**
     * 识别「异地患者须走开通异地服务的就医地区划」类平台错误。
     *
     * @param errMsg 平台 err_msg
     * @return 是否异地就医地区划错误
     */
    public static boolean isOffsiteTreatmentAreaError(String errMsg) {
        if (isEmpty(errMsg)) {
            return false;
        }
        String err = errMsg.trim();
        return err.contains("异地患者")
            || err.contains("开通异地服务的就医地医保区划");
    }
}
