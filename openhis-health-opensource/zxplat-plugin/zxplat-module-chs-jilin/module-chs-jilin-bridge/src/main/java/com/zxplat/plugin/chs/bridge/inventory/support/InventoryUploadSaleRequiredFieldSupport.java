/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.inventory.command.InvUploadLineCommand;
import com.zxplat.plugin.chs.domain.share.enums.MdtrtSetlType;

/**
 * 3505/3506 规范必填项校验：缺则返回失败原因（业务主数据应齐全，不上报臆测兜底）。
 *
 * <p>采购类 3503/3504 发票号走占位策略，不在本类校验。</p>
 */
public final class InventoryUploadSaleRequiredFieldSupport {

    private InventoryUploadSaleRequiredFieldSupport() {
    }

    /**
     * 校验销售/退货上报行必填项。
     *
     * @param line     已组装行
     * @param saleFlow true=3505；false=3506（3506 不要求开方医师/药师执业证/外购标志等销售专属）
     * @return 失败中文原因；通过则 null
     */
    public static String resolveMissingRequiredMessage(InvUploadLineCommand line, boolean saleFlow) {
        // 空行无法上报
        if (line == null) {
            return "上报行为空";
        }
        // 业务规则预置失败（如配药人=发药人）优先于字段缺失
        if (isNotEmpty(line.getPreUploadFailureMessage())) {
            return line.getPreUploadFailureMessage().trim();
        }
        // 公共：医保目录编码（须 3301 已对码）
        if (isEmpty(line.getMedListCodg())) {
            return "医疗目录编码(med_list_codg)为空，业务主数据异常";
        }
        // 公共：机构目录编号
        if (isEmpty(line.getFixmedinsHilistId())) {
            return "机构目录编号(fixmedins_hilist_id)为空，业务主数据异常";
        }
        // 公共：机构目录名称
        if (isEmpty(line.getFixmedinsHilistName())) {
            return "机构目录名称(fixmedins_hilist_name)为空，业务主数据异常";
        }
        // 公共：机构批次流水号（幂等键）
        if (isEmpty(line.getFixmedinsBchno())) {
            return "机构批次流水号(fixmedins_bchno)为空，业务主数据异常";
        }
        // 公共：生产批号
        if (isEmpty(line.getManuLotnum())) {
            return "生产批号(manu_lotnum)为空，业务主数据异常";
        }
        // 公共：生产日期
        if (isEmpty(line.getManuDate())) {
            return "生产日期(manu_date)为空，业务主数据异常";
        }
        // 公共：处方药标志
        if (isEmpty(line.getRxFlag())) {
            return "处方药标志(rx_flag)为空，业务主数据异常";
        }
        // 公共：拆零标志
        if (isEmpty(line.getTrdnFlag())) {
            return "拆零标志(trdn_flag)为空，业务主数据异常";
        }
        // 公共：销售/退货数量
        if (line.getSelRetnCnt() == null) {
            return "销售/退货数量(sel_retn_cnt)为空，业务主数据异常";
        }
        // 公共：销售/退货时间
        if (isEmpty(line.getSelRetnTime())) {
            return "销售/退货时间(sel_retn_time)为空，业务主数据异常";
        }
        // 公共：经办人（3505=配药人；3506=配药人或退药确认人）
        if (isEmpty(line.getSelRetnOpterName())) {
            return "销售/退货经办人(sel_retn_opter_name)为空，业务主数据异常";
        }
        // 公共：人员证件类型
        if (isEmpty(line.getPsnCertType())) {
            return "人员证件类型(psn_cert_type)为空，业务主数据异常";
        }
        // 公共：就医流水号（对人关联）
        if (isEmpty(line.getMdtrtSn())) {
            return "就医流水号(mdtrt_sn)为空，业务主数据异常";
        }
        // 3505 销售专属必填
        if (saleFlow) {
            if (isEmpty(line.getHiFeesetlType())) {
                return "医保费用结算类型(hi_feesetl_type)为空，业务主数据异常";
            }
            if (isEmpty(line.getPrscDrName())) {
                return "开方医师姓名(prsc_dr_name)为空，业务主数据异常";
            }
            if (isEmpty(line.getPharName())) {
                return "药师姓名(phar_name)为空，业务主数据异常";
            }
            if (isEmpty(line.getPharPracCertNo())) {
                return "药师执业资格证号(phar_prac_cert_no)为空，业务主数据异常";
            }
            if (isEmpty(line.getRxCircFlag())) {
                return "外购处方标志(rx_circ_flag)为空，业务主数据异常";
            }
            if (isEmpty(line.getRtalDocno())) {
                return "零售单据号(rtal_docno/feedetl_sn)为空，业务主数据异常";
            }
            if (isEmpty(line.getMdtrtSetlType())) {
                return "就诊结算类型(mdtrt_setl_type)为空，业务主数据异常";
            }
            // 医保结算须有结算 ID（自费不要求）
            if (MdtrtSetlType.INSURANCE.getCode().equals(line.getMdtrtSetlType())
                && isEmpty(line.getSetlId())) {
                return "医保结算ID(setl_id)为空，业务主数据异常";
            }
        } else {
            // 3506 对单（吉林）：须引用 3511 平台销售流水 medinsProdSelNo
            if (isEmpty(line.getMedinsProdSelNo())) {
                return "商品销售流水号(medins_prod_sel_no/平台流水)为空，无法关联原销售";
            }
            // 医保退货 setl_id 必填（与 3505 医保口径一致）
            if (MdtrtSetlType.INSURANCE.getCode().equals(line.getMdtrtSetlType())
                && isEmpty(line.getSetlId())) {
                return "医保结算ID(setl_id)为空，业务主数据异常";
            }
        }
        return null;
    }
}
