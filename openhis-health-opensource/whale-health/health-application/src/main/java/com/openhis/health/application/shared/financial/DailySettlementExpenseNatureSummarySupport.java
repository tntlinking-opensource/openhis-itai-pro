/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

import com.openhis.health.application.shared.financial.dto.DailySettlementExpenseNatureSummaryDto;
import com.openhis.health.application.shared.financial.dto.DailySettlementInsuranceTypeSummaryDto;
import com.openhis.health.application.shared.financial.dto.DailySettlementSummaryRowDto;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 日结下半区费用性质 / 险种动态块装配（门急诊与住院共用）。
 */
public final class DailySettlementExpenseNatureSummarySupport {

    private DailySettlementExpenseNatureSummarySupport() {
    }

    /** 未标注合同业务号时的展示名。 */
    public static final String UNLABELED_CONTRACT_NAME = "未标注费用性质";

    /** 未解析到 insutype 时的展示名。 */
    public static final String UNKNOWN_INSUTYPE_NAME = "未分险种";

    /**
     * 1901 字典未命中时的基线险种名（与前端 {@code reconciliation.ts} 同构）。
     */
    private static final Map<String, String> INSUTYPE_LABEL_FALLBACK = Map.of(
        "310", "职工基本医疗保险",
        "390", "城乡居民基本医疗保险",
        "340", "离休人员医疗保障"
    );

    /**
     * 解析险种展示名：字典 label → 基线兜底 → 原码 → 未分险种。
     *
     * @param insutype     平台险种码
     * @param labelByCode  1901 字典映射（可空）
     * @return 非空展示名
     */
    public static String resolveInsutypeDisplayName(String insutype, Map<String, String> labelByCode) {
        if (StringUtils.isEmpty(insutype)) {
            return UNKNOWN_INSUTYPE_NAME;
        }
        String code = insutype.trim();
        if (labelByCode != null && labelByCode.containsKey(code)) {
            String label = labelByCode.get(code);
            if (StringUtils.isNotEmpty(label)) {
                return label.trim();
            }
        }
        String fallback = INSUTYPE_LABEL_FALLBACK.get(code);
        if (StringUtils.isNotEmpty(fallback)) {
            return fallback;
        }
        return code;
    }

    /**
     * 费用性质累加桶。
     */
    public static final class NatureBucket {
        private String contractNo = "";
        private String contractName = UNLABELED_CONTRACT_NAME;
        private boolean insurance;
        private final Set<Long> personKeys = new LinkedHashSet<>();
        private BigDecimal cashAmount = BigDecimal.ZERO;
        private BigDecimal fundAmount = BigDecimal.ZERO;
        private BigDecimal accountAmount = BigDecimal.ZERO;
        private BigDecimal fundTotalAmount = BigDecimal.ZERO;
        private BigDecimal netTotal = BigDecimal.ZERO;
        private final Map<String, InsutypeBucket> byInsutype = new LinkedHashMap<>();

        public String getContractNo() {
            return contractNo;
        }

        public void setContractNo(String contractNo) {
            this.contractNo = contractNo == null ? "" : contractNo;
        }

        public String getContractName() {
            return contractName;
        }

        public void setContractName(String contractName) {
            this.contractName = contractName;
        }

        public boolean isInsurance() {
            return insurance;
        }

        public void setInsurance(boolean insurance) {
            this.insurance = insurance;
        }

        public Set<Long> getPersonKeys() {
            return personKeys;
        }

        public BigDecimal getCashAmount() {
            return cashAmount;
        }

        public void addCash(BigDecimal amount) {
            this.cashAmount = this.cashAmount.add(nullToZero(amount));
        }

        public BigDecimal getFundAmount() {
            return fundAmount;
        }

        public void addFund(BigDecimal amount) {
            BigDecimal value = nullToZero(amount);
            this.fundAmount = this.fundAmount.add(value);
            this.fundTotalAmount = this.fundTotalAmount.add(value);
        }

        public BigDecimal getAccountAmount() {
            return accountAmount;
        }

        public void addAccount(BigDecimal amount) {
            this.accountAmount = this.accountAmount.add(nullToZero(amount));
        }

        public BigDecimal getFundTotalAmount() {
            return fundTotalAmount;
        }

        public BigDecimal getNetTotal() {
            return netTotal;
        }

        public void addNetTotal(BigDecimal amount) {
            this.netTotal = this.netTotal.add(nullToZero(amount));
        }

        public Map<String, InsutypeBucket> getByInsutype() {
            return byInsutype;
        }

        public InsutypeBucket ensureInsutype(String insutype, String insutypeName) {
            String key = insutype == null ? "" : insutype.trim();
            InsutypeBucket bucket = byInsutype.get(key);
            if (bucket == null) {
                bucket = new InsutypeBucket();
                bucket.setInsutype(key);
                bucket.setInsutypeName(insutypeName);
                byInsutype.put(key, bucket);
            }
            return bucket;
        }
    }

    /**
     * 险种子桶。
     */
    public static final class InsutypeBucket {
        private String insutype = "";
        private String insutypeName = UNKNOWN_INSUTYPE_NAME;
        private final Set<Long> personKeys = new LinkedHashSet<>();
        private BigDecimal cashAmount = BigDecimal.ZERO;
        private BigDecimal fundAmount = BigDecimal.ZERO;
        private BigDecimal accountAmount = BigDecimal.ZERO;
        private BigDecimal fundTotalAmount = BigDecimal.ZERO;

        public String getInsutype() {
            return insutype;
        }

        public void setInsutype(String insutype) {
            this.insutype = insutype == null ? "" : insutype;
        }

        public String getInsutypeName() {
            return insutypeName;
        }

        public void setInsutypeName(String insutypeName) {
            this.insutypeName = insutypeName;
        }

        public Set<Long> getPersonKeys() {
            return personKeys;
        }

        public BigDecimal getCashAmount() {
            return cashAmount;
        }

        public void addCash(BigDecimal amount) {
            this.cashAmount = this.cashAmount.add(nullToZero(amount));
        }

        public BigDecimal getFundAmount() {
            return fundAmount;
        }

        public void addFund(BigDecimal amount) {
            BigDecimal value = nullToZero(amount);
            this.fundAmount = this.fundAmount.add(value);
            this.fundTotalAmount = this.fundTotalAmount.add(value);
        }

        public BigDecimal getAccountAmount() {
            return accountAmount;
        }

        public void addAccount(BigDecimal amount) {
            this.accountAmount = this.accountAmount.add(nullToZero(amount));
        }

        public BigDecimal getFundTotalAmount() {
            return fundTotalAmount;
        }
    }

    /**
     * 取得或创建费用性质桶。
     */
    public static NatureBucket ensureNature(
        Map<String, NatureBucket> natureByContractNo,
        String contractNo,
        String contractName,
        boolean insurance) {
        String key = contractNo == null ? "" : contractNo.trim();
        NatureBucket bucket = natureByContractNo.get(key);
        if (bucket == null) {
            bucket = new NatureBucket();
            bucket.setContractNo(key);
            bucket.setContractName(StringUtils.isNotEmpty(contractName) ? contractName : (
                StringUtils.isEmpty(key) ? UNLABELED_CONTRACT_NAME : key));
            bucket.setInsurance(insurance);
            natureByContractNo.put(key, bucket);
        } else if (insurance) {
            bucket.setInsurance(true);
        }
        return bucket;
    }

    /**
     * 转为响应 DTO 列表。
     */
    public static List<DailySettlementExpenseNatureSummaryDto> toExpenseNatureSummaries(
        Map<String, NatureBucket> natureByContractNo) {
        List<DailySettlementExpenseNatureSummaryDto> out = new ArrayList<>();
        if (natureByContractNo == null || natureByContractNo.isEmpty()) {
            return out;
        }
        for (NatureBucket bucket : natureByContractNo.values()) {
            if (bucket == null) {
                continue;
            }
            DailySettlementExpenseNatureSummaryDto dto = new DailySettlementExpenseNatureSummaryDto();
            dto.setContractNo(bucket.getContractNo());
            dto.setContractName(bucket.getContractName());
            dto.setInsurance(bucket.isInsurance());
            dto.setPersonCount(bucket.getPersonKeys().size());
            dto.setCashAmount(formatAmount(bucket.getCashAmount()));
            dto.setFundAmount(formatAmount(bucket.getFundAmount()));
            dto.setAccountAmount(formatAmount(bucket.getAccountAmount()));
            dto.setFundTotalAmount(formatAmount(bucket.getFundTotalAmount()));
            if (bucket.isInsurance()) {
                dto.setTotalAmount(formatAmount(
                    bucket.getFundAmount().add(bucket.getAccountAmount())));
            } else {
                dto.setTotalAmount(formatAmount(bucket.getNetTotal()));
            }
            List<DailySettlementInsuranceTypeSummaryDto> typeRows = new ArrayList<>();
            if (bucket.isInsurance()) {
                for (InsutypeBucket typeBucket : bucket.getByInsutype().values()) {
                    if (typeBucket == null) {
                        continue;
                    }
                    DailySettlementInsuranceTypeSummaryDto typeDto =
                        new DailySettlementInsuranceTypeSummaryDto();
                    typeDto.setInsutype(typeBucket.getInsutype());
                    typeDto.setInsutypeName(typeBucket.getInsutypeName());
                    typeDto.setPersonCount(typeBucket.getPersonKeys().size());
                    typeDto.setCashAmount(formatAmount(typeBucket.getCashAmount()));
                    typeDto.setFundAmount(formatAmount(typeBucket.getFundAmount()));
                    typeDto.setAccountAmount(formatAmount(typeBucket.getAccountAmount()));
                    typeDto.setFundTotalAmount(formatAmount(typeBucket.getFundTotalAmount()));
                    typeDto.setTotalAmount(formatAmount(
                        typeBucket.getFundAmount().add(typeBucket.getAccountAmount())));
                    typeRows.add(typeDto);
                }
            }
            dto.setInsuranceTypeSummaries(typeRows);
            out.add(dto);
        }
        return out;
    }

    /**
     * 医保补偿合计：各医保费用性质统筹（基金）代数和。
     *
     * @param natureByContractNo 费用性质累加桶
     * @return 补偿合计（展示字符串）
     */
    public static String sumInsuranceCompensationAmount(Map<String, NatureBucket> natureByContractNo) {
        BigDecimal total = BigDecimal.ZERO;
        if (natureByContractNo == null || natureByContractNo.isEmpty()) {
            return formatAmount(total);
        }
        for (NatureBucket bucket : natureByContractNo.values()) {
            if (bucket != null && bucket.isInsurance()) {
                total = total.add(nullToZero(bucket.getFundAmount()));
            }
        }
        return formatAmount(total);
    }

    /**
     * 装配下半区扁平展示行。
     *
     * @param totalIncome                   总收入
     * @param wechatAmount                  微信
     * @param alipayAmount                  支付宝
     * @param onlineBankAmount              银行卡
     * @param cashDueAmount                 应交现金（现金渠道）
     * @param insuranceCompensationAmount   医保补偿合计
     * @param depositAmount                 预交金（住院）；门诊传 null 跳过
     * @param natures                       费用性质块
     * @param physicalExamCount             体检人次；null 跳过
     */
    public static List<DailySettlementSummaryRowDto> buildSummaryRows(
        String totalIncome,
        String wechatAmount,
        String alipayAmount,
        String onlineBankAmount,
        String cashDueAmount,
        String insuranceCompensationAmount,
        String depositAmount,
        List<DailySettlementExpenseNatureSummaryDto> natures,
        Integer physicalExamCount) {
        List<DailySettlementSummaryRowDto> rows = new ArrayList<>();
        rows.add(row("totalIncome", "总收入", totalIncome, "元", 0));
        rows.add(row("wechatAmount", "微信", wechatAmount, "元", 0));
        rows.add(row("alipayAmount", "支付宝", alipayAmount, "元", 0));
        rows.add(row("onlineBankAmount", "银行卡", onlineBankAmount, "元", 0));
        rows.add(row("cashDueAmount", "应交现金", cashDueAmount, "元", 0));
        rows.add(row("insuranceCompensationAmount", "医保补偿", insuranceCompensationAmount, "元", 0));
        if (depositAmount != null) {
            rows.add(row("depositAmount", "预交金", depositAmount, "元", 0));
        }
        if (natures != null) {
            int natureIndex = 0;
            for (DailySettlementExpenseNatureSummaryDto nature : natures) {
                if (nature == null) {
                    continue;
                }
                String name = StringUtils.isNotEmpty(nature.getContractName())
                    ? nature.getContractName()
                    : UNLABELED_CONTRACT_NAME;
                String prefix = "nature." + natureIndex;
                rows.add(row(
                    prefix + ".personCount",
                    name + "人次",
                    nature.getPersonCount() == null ? "0" : String.valueOf(nature.getPersonCount()),
                    "人",
                    0));
                if (Boolean.TRUE.equals(nature.getInsurance())) {
                    rows.add(row(prefix + ".cashAmount", name + "现金", nature.getCashAmount(), "元", 0));
                    rows.add(row(prefix + ".fundAmount", name + "统筹", nature.getFundAmount(), "元", 0));
                    rows.add(row(prefix + ".accountAmount", name + "账户", nature.getAccountAmount(), "元", 0));
                    rows.add(row(prefix + ".totalAmount", name + "总额", nature.getTotalAmount(), "元", 0));
                    int typeIndex = 0;
                    List<DailySettlementInsuranceTypeSummaryDto> types = nature.getInsuranceTypeSummaries();
                    if (types != null) {
                        for (DailySettlementInsuranceTypeSummaryDto type : types) {
                            if (type == null) {
                                continue;
                            }
                            String typeName = StringUtils.isNotEmpty(type.getInsutypeName())
                                ? type.getInsutypeName()
                                : UNKNOWN_INSUTYPE_NAME;
                            // 「未分险种」与费用性质块重复，不输出子行
                            if (UNKNOWN_INSUTYPE_NAME.equals(typeName)) {
                                continue;
                            }
                            String typePrefix = prefix + ".insu." + typeIndex;
                            rows.add(row(
                                typePrefix + ".personCount",
                                typeName + "人次",
                                type.getPersonCount() == null ? "0" : String.valueOf(type.getPersonCount()),
                                "人",
                                1));
                            rows.add(row(typePrefix + ".cashAmount", typeName + "现金", type.getCashAmount(), "元", 1));
                            rows.add(row(typePrefix + ".fundAmount", typeName + "统筹", type.getFundAmount(), "元", 1));
                            rows.add(row(
                                typePrefix + ".accountAmount", typeName + "账户", type.getAccountAmount(), "元", 1));
                            rows.add(row(
                                typePrefix + ".totalAmount", typeName + "总额", type.getTotalAmount(), "元", 1));
                            typeIndex++;
                        }
                    }
                } else {
                    rows.add(row(prefix + ".totalAmount", name + "总额", nature.getTotalAmount(), "元", 0));
                }
                natureIndex++;
            }
        }
        if (physicalExamCount != null) {
            rows.add(row(
                "physicalExamCount",
                "体检人次",
                String.valueOf(physicalExamCount),
                "人",
                0));
        }
        return rows;
    }

    private static DailySettlementSummaryRowDto row(
        String key, String label, String value, String unit, int level) {
        DailySettlementSummaryRowDto dto = new DailySettlementSummaryRowDto();
        dto.setKey(key);
        dto.setLabel(label);
        dto.setValue(value == null ? ( "人".equals(unit) ? "0" : "0.00") : value);
        dto.setUnit(unit);
        dto.setLevel(level);
        return dto;
    }

    private static BigDecimal nullToZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static String formatAmount(BigDecimal amount) {
        return MonetaryAmountSupport.scaleDisplay(nullToZero(amount)).toPlainString();
    }
}
