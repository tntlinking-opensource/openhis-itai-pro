/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.medcas;

import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasHomepageUploadExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasSettleListQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasSettleListStatusExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionIn.MedcasSettleListUploadExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionOut.MedcasUploadExtensionOut;

/**
 * 病案医保上报 SPI（4401 / 4101A / 4102 / 4103）。
 *
 * <p>health 核心 → CHS Module；未装配时默认禁用。</p>
 */
public interface MedcasHomepageUploadExtension {

    /**
     * 病案医保上报扩展是否已装配。
     *
     * @return false 时 MRMS 上传返回未装配提示
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 上传住院病案首页至医保平台（4401）。
     *
     * @param command 合同、inputJson 与操作员
     * @return 平台 infcode 与 output 摘要
     */
    default MedcasUploadExtensionOut upload(MedcasHomepageUploadExtensionIn command) {
        MedcasUploadExtensionOut result = new MedcasUploadExtensionOut();
        result.setSuccess(false);
        result.setErrMsg("医保网关未装配，请启用 module-chs-jilin-starter");
        return result;
    }

    /**
     * 上传医疗保障基金结算清单（4101A）。
     *
     * @param command 合同、4101A inputJson 与操作员
     * @return 平台结果（成功时 output 含 setl_list_id）
     */
    default MedcasUploadExtensionOut uploadSettleList(MedcasSettleListUploadExtensionIn command) {
        MedcasUploadExtensionOut result = new MedcasUploadExtensionOut();
        result.setSuccess(false);
        result.setErrMsg("医保网关未装配，请启用 module-chs-jilin-starter");
        return result;
    }

    /**
     * 修改结算清单状态（4102：0 未提交 / 1 已提交）。
     *
     * @param command 合同与状态入参
     * @return 平台结果
     */
    default MedcasUploadExtensionOut modifySettleListStatus(MedcasSettleListStatusExtensionIn command) {
        MedcasUploadExtensionOut result = new MedcasUploadExtensionOut();
        result.setSuccess(false);
        result.setErrMsg("医保网关未装配，请启用 module-chs-jilin-starter");
        return result;
    }

    /**
     * 查询结算清单信息（4103）。
     *
     * @param command 合同与 psn_no/setl_id
     * @return 平台结果（成功时 output 含 setlinfo）
     */
    default MedcasUploadExtensionOut querySettleList(MedcasSettleListQueryExtensionIn command) {
        MedcasUploadExtensionOut result = new MedcasUploadExtensionOut();
        result.setSuccess(false);
        result.setErrMsg("医保网关未装配，请启用 module-chs-jilin-starter");
        return result;
    }
}
