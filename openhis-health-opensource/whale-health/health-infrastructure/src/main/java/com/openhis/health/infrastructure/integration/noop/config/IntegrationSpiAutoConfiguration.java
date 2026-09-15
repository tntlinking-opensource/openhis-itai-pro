/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.noop.config;

import com.openhis.health.infrastructure.integration.booking.inpatient.InpatientBedBookingConsumeExtension;
import com.openhis.health.infrastructure.integration.booking.inpatient.InpatientBedBookingQueryExtension;
import com.openhis.health.infrastructure.integration.booking.outpatient.OutpatientBookingQueryExtension;
import com.openhis.health.infrastructure.integration.booking.outpatient.OutpatientBookingRegistrationExtension;
import com.openhis.health.infrastructure.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.openhis.health.infrastructure.integration.insurance.contract.InsuranceContractTerminalExtension;
import com.openhis.health.infrastructure.integration.insurance.inpatient.InpatientChargeInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.settlement.InsurancePaymentSettlementExtension;
import com.openhis.health.infrastructure.integration.insurance.settlement.InsuranceSettlementRecordLookupExtension;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryApprovalUploadExtension;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryEncounterContextExtension;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryInvokeExtension;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryModuleExtension;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryOutpatientSettlementExtension;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryReconciliationExtension;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryRegisterExtension;
import com.openhis.health.infrastructure.integration.insurance.platformdict.InsurancePlatformDictDisplayExtension;
import com.openhis.health.infrastructure.integration.emr.EmrAdmissionDiagnosisSyncExtension;
import com.openhis.health.infrastructure.integration.einvoice.BossElectronicInvoiceExtension;
import com.openhis.health.infrastructure.integration.elep.ElepPrescriptionExtension;
import com.openhis.health.infrastructure.integration.insurance.catalog.InsuranceDiseaseCatalogSourceExtension;
import com.openhis.health.infrastructure.integration.insurance.catalog.InsuranceSurgeryCatalogSourceExtension;
import com.openhis.health.infrastructure.integration.insurance.medcas.MedcasHomepageUploadExtension;
import com.openhis.health.infrastructure.integration.lis.LisAdviceApplyExtension;
import com.openhis.health.infrastructure.integration.pacs.PacsAdviceApplyExtension;
import com.openhis.health.infrastructure.integration.emr.EmrAdmissionConditionExtension;
import com.openhis.health.infrastructure.integration.emr.EmrDocumentExtension;
import com.openhis.health.infrastructure.integration.notification.StaffNotificationExtension;
import com.openhis.health.infrastructure.integration.payment.ThirdPartyPaymentExtension;
import com.openhis.health.infrastructure.integration.noop.booking.NoOpInpatientBedBookingConsumeExtension;
import com.openhis.health.infrastructure.integration.noop.booking.NoOpInpatientBedBookingQueryExtension;
import com.openhis.health.infrastructure.integration.noop.booking.NoOpOutpatientBookingQueryExtension;
import com.openhis.health.infrastructure.integration.noop.booking.NoOpOutpatientBookingRegistrationExtension;
import com.openhis.health.infrastructure.integration.noop.emr.NoOpEmrAdmissionDiagnosisSyncExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.catalog.NoOpInsuranceDiseaseCatalogSourceExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.catalog.NoOpInsuranceSurgeryCatalogSourceExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.medcas.NoOpMedcasHomepageUploadExtension;
import com.openhis.health.infrastructure.integration.noop.lis.NoOpLisAdviceApplyExtension;
import com.openhis.health.infrastructure.integration.noop.pacs.NoOpPacsAdviceApplyExtension;
import com.openhis.health.infrastructure.integration.noop.emr.NoOpEmrAdmissionConditionExtension;
import com.openhis.health.infrastructure.integration.noop.emr.NoOpEmrDocumentExtension;
import com.openhis.health.infrastructure.integration.noop.notification.LoggingStaffNotificationExtension;
import com.openhis.health.infrastructure.integration.noop.einvoice.NoOpBossElectronicInvoiceExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.dict.NoOpInsurancePlatformDictDisplayExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.benefit.NoOpInsuranceRegisterBenefitQueryExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.contract.NoOpInsuranceContractTerminalExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.inpatient.NoOpInpatientChargeInsuranceExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.inventory.NoOpInventoryThirdPartyUploadExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.outpatient.NoOpOutpatientChargeInsuranceExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.outpatient.NoOpOutpatientRegistrationInsuranceExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.settlement.NoOpInsurancePaymentSettlementExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.settlement.NoOpInsuranceSettlementRecordLookupExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.workinjury.NoOpWorkInjuryApprovalUploadExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.workinjury.NoOpWorkInjuryEncounterContextExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.workinjury.NoOpWorkInjuryInvokeExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.workinjury.NoOpWorkInjuryModuleExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.workinjury.NoOpWorkInjuryOutpatientSettlementExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.workinjury.NoOpWorkInjuryReconciliationExtension;
import com.openhis.health.infrastructure.integration.noop.insurance.workinjury.NoOpWorkInjuryRegisterExtension;
import com.openhis.health.infrastructure.integration.noop.elep.NoOpElepPrescriptionExtension;
import com.openhis.health.infrastructure.integration.noop.payment.NoOpThirdPartyPaymentExtension;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 中性集成契约默认 Bean（NoOp）；省份 Module adapter 通过 {@code @Primary} + {@code @ConditionalOnBean} 覆盖。
 *
 * <p>医保相关：未装 chs-jilin 时 {@link OutpatientChargeInsuranceExtension#isEnabled()} 为 false，
 * health-application 自动降级为纯自费收银，无需改业务代码。</p>
 */
@AutoConfiguration
public class IntegrationSpiAutoConfiguration {

    /**
     * 1901 平台字典展示 Extension 占位（吉林覆盖为 {@code InsurancePlatformDictDisplayExtensionImpl}）。
     *
     * @return {@link InsurancePlatformDictDisplayExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(InsurancePlatformDictDisplayExtension.class)
    InsurancePlatformDictDisplayExtension noOpInsurancePlatformDictDisplayExtension() {
        return new NoOpInsurancePlatformDictDisplayExtension();
    }

    /**
     * 结算回执 insutype lookup 占位（吉林覆盖为 {@code InsuranceSettlementRecordLookupExtensionImpl}）。
     *
     * @return {@link InsuranceSettlementRecordLookupExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(InsuranceSettlementRecordLookupExtension.class)
    InsuranceSettlementRecordLookupExtension noOpInsuranceSettlementRecordLookupExtension() {
        return new NoOpInsuranceSettlementRecordLookupExtension();
    }

    /**
     * 契约终端参数（fixmedins/adm_vs）占位（吉林覆盖为 {@code InsuranceContractTerminalExtensionImpl}）。
     *
     * @return {@link InsuranceContractTerminalExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(InsuranceContractTerminalExtension.class)
    InsuranceContractTerminalExtension noOpInsuranceContractTerminalExtension() {
        return new NoOpInsuranceContractTerminalExtension();
    }

    /**
     * 门诊收银医保 Extension 占位（吉林 Module 覆盖为 {@code OutpatientChargeInsuranceExtensionImpl}）。
     *
     * @return {@link OutpatientChargeInsuranceExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(OutpatientChargeInsuranceExtension.class)
    OutpatientChargeInsuranceExtension noOpOutpatientChargeInsuranceExtension() {
        return new NoOpOutpatientChargeInsuranceExtension();
    }

    /**
     * 收银落账后医保挂接 Extension 占位（吉林 Module 覆盖为 {@code InsurancePaymentSettlementExtensionImpl}）。
     *
     * @return {@link InsurancePaymentSettlementExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(InsurancePaymentSettlementExtension.class)
    InsurancePaymentSettlementExtension noOpInsurancePaymentSettlementExtension() {
        return new NoOpInsurancePaymentSettlementExtension();
    }

    /**
     * 工伤 Module 能力门控 Extension 占位（wi-jilin 覆盖为 {@code WorkInjuryModuleExtensionImpl}）。
     *
     * @return {@link WorkInjuryModuleExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(WorkInjuryModuleExtension.class)
    WorkInjuryModuleExtension noOpWorkInjuryModuleExtension() {
        return new NoOpWorkInjuryModuleExtension();
    }

    /**
     * 工伤登记 Extension 占位（wi-jilin 覆盖为 {@code WorkInjuryRegisterExtensionImpl}）。
     *
     * @return {@link WorkInjuryRegisterExtension} 默认实现
     */
    @Bean
    @ConditionalOnMissingBean(WorkInjuryRegisterExtension.class)
    WorkInjuryRegisterExtension noOpWorkInjuryRegisterExtension() {
        return new NoOpWorkInjuryRegisterExtension();
    }

    /**
     * 工伤审批上传 Extension 占位（wi-jilin 覆盖为 {@code WorkInjuryApprovalUploadExtensionImpl}）。
     *
     * @return {@link WorkInjuryApprovalUploadExtension} 默认实现
     */
    @Bean
    @ConditionalOnMissingBean(WorkInjuryInvokeExtension.class)
    WorkInjuryInvokeExtension noOpWorkInjuryInvokeExtension() {
        return new NoOpWorkInjuryInvokeExtension();
    }

    @Bean
    @ConditionalOnMissingBean(WorkInjuryApprovalUploadExtension.class)
    WorkInjuryApprovalUploadExtension noOpWorkInjuryApprovalUploadExtension() {
        return new NoOpWorkInjuryApprovalUploadExtension();
    }

    /**
     * 工伤门诊结算 Extension 占位（wi-jilin 覆盖为 {@code WorkInjuryOutpatientSettlementExtensionImpl}）。
     *
     * @return {@link WorkInjuryOutpatientSettlementExtension} 默认实现
     */
    @Bean
    @ConditionalOnMissingBean(WorkInjuryOutpatientSettlementExtension.class)
    WorkInjuryOutpatientSettlementExtension noOpWorkInjuryOutpatientSettlementExtension() {
        return new NoOpWorkInjuryOutpatientSettlementExtension();
    }

    /**
     * 工伤对账 Extension 占位（wi-jilin 覆盖为 {@code WorkInjuryReconciliationExtensionImpl}）。
     *
     * @return {@link WorkInjuryReconciliationExtension} 默认实现
     */
    @Bean
    @ConditionalOnMissingBean(WorkInjuryReconciliationExtension.class)
    WorkInjuryReconciliationExtension noOpWorkInjuryReconciliationExtension() {
        return new NoOpWorkInjuryReconciliationExtension();
    }

    /**
     * 工伤就诊上下文 Extension 占位（wi-jilin 覆盖为 {@code WorkInjuryEncounterContextExtensionImpl}）。
     *
     * @return {@link WorkInjuryEncounterContextExtension} 默认实现
     */
    @Bean
    @ConditionalOnMissingBean(WorkInjuryEncounterContextExtension.class)
    WorkInjuryEncounterContextExtension noOpWorkInjuryEncounterContextExtension() {
        return new NoOpWorkInjuryEncounterContextExtension();
    }

    /**
     * 门诊挂号 2201 Extension 占位（吉林覆盖为 {@code OutpatientRegistrationInsuranceExtensionImpl}）。
     *
     * @return {@link OutpatientRegistrationInsuranceExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(OutpatientRegistrationInsuranceExtension.class)
    OutpatientRegistrationInsuranceExtension noOpOutpatientRegistrationInsuranceExtension() {
        return new NoOpOutpatientRegistrationInsuranceExtension();
    }

    /**
     * 住院结算 Extension 占位（吉林覆盖为 {@code InpatientChargeInsuranceExtensionImpl}）。
     *
     * @return {@link InpatientChargeInsuranceExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(InpatientChargeInsuranceExtension.class)
    InpatientChargeInsuranceExtension noOpInpatientChargeInsuranceExtension() {
        return new NoOpInpatientChargeInsuranceExtension();
    }

    /**
     * 进销存上报 Extension 占位（吉林覆盖为 {@code InventoryThirdPartyUploadExtensionImpl}）。
     *
     * @return {@link InventoryThirdPartyUploadExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(InventoryThirdPartyUploadExtension.class)
    InventoryThirdPartyUploadExtension noOpInventoryThirdPartyUploadExtension() {
        return new NoOpInventoryThirdPartyUploadExtension();
    }

    /**
     * 电子处方 Extension 占位（省份 Module 覆盖为 {@code ElepPrescriptionExtensionImpl}）。
     *
     * @return {@link ElepPrescriptionExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(ElepPrescriptionExtension.class)
    ElepPrescriptionExtension noOpElepPrescriptionExtension() {
        return new NoOpElepPrescriptionExtension();
    }

    /**
     * 挂号待遇查询 Extension 占位（吉林覆盖为 {@code InsuranceRegisterBenefitQueryExtensionImpl}）。
     *
     * @return {@link InsuranceRegisterBenefitQueryExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(InsuranceRegisterBenefitQueryExtension.class)
    InsuranceRegisterBenefitQueryExtension noOpInsuranceRegisterBenefitQueryExtension() {
        return new NoOpInsuranceRegisterBenefitQueryExtension();
    }

    /**
     * 入院诊断同步 EMR Extension 占位（EMR Module 覆盖为 {@code EmrAdmissionDiagnosisSyncExtensionImpl}）。
     *
     * @return {@link EmrAdmissionDiagnosisSyncExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(EmrAdmissionDiagnosisSyncExtension.class)
    EmrAdmissionDiagnosisSyncExtension noOpEmrAdmissionDiagnosisSyncExtension() {
        return new NoOpEmrAdmissionDiagnosisSyncExtension();
    }

    /**
     * 门诊预约查询 Extension 占位（booking Module 覆盖为 {@code OutpatientBookingQueryExtensionImpl}）。
     *
     * @return {@link OutpatientBookingQueryExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(OutpatientBookingQueryExtension.class)
    OutpatientBookingQueryExtension noOpOutpatientBookingQueryExtension() {
        return new NoOpOutpatientBookingQueryExtension();
    }

    /**
     * 门诊预约登记校验 Extension 占位（booking Module 覆盖为 {@code OutpatientBookingRegistrationExtensionImpl}）。
     *
     * @return {@link OutpatientBookingRegistrationExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(OutpatientBookingRegistrationExtension.class)
    OutpatientBookingRegistrationExtension noOpOutpatientBookingRegistrationExtension() {
        return new NoOpOutpatientBookingRegistrationExtension();
    }

    /**
     * 住院床位预约查询 Extension 占位（booking Module 覆盖为 {@code InpatientBedBookingQueryExtensionImpl}）。
     *
     * @return {@link InpatientBedBookingQueryExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(InpatientBedBookingQueryExtension.class)
    InpatientBedBookingQueryExtension noOpInpatientBedBookingQueryExtension() {
        return new NoOpInpatientBedBookingQueryExtension();
    }

    /**
     * 住院床位预约占用/释放 Extension 占位（booking Module 覆盖为 {@code InpatientBedBookingConsumeExtensionImpl}）。
     *
     * @return {@link InpatientBedBookingConsumeExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(InpatientBedBookingConsumeExtension.class)
    InpatientBedBookingConsumeExtension noOpInpatientBedBookingConsumeExtension() {
        return new NoOpInpatientBedBookingConsumeExtension();
    }

    /**
     * 博思电子票据 Extension 占位（einvoice-boss Module 覆盖为 {@code BossElectronicInvoiceExtensionImpl}）。
     *
     * @return {@link BossElectronicInvoiceExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(BossElectronicInvoiceExtension.class)
    BossElectronicInvoiceExtension noOpBossElectronicInvoiceExtension() {
        return new NoOpBossElectronicInvoiceExtension();
    }

    /**
     * 第三方支付 Extension 占位（unionpay-bpc Module 覆盖为 {@code ThirdPartyPaymentExtensionImpl}）。
     *
     * @return {@link ThirdPartyPaymentExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(ThirdPartyPaymentExtension.class)
    ThirdPartyPaymentExtension noOpThirdPartyPaymentExtension() {
        return new NoOpThirdPartyPaymentExtension();
    }

    /**
     * EMR 文书 Extension 占位（EMR Module 覆盖为 {@code EmrDocumentExtensionImpl}）。
     *
     * @return {@link EmrDocumentExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(EmrDocumentExtension.class)
    EmrDocumentExtension noOpEmrDocumentExtension() {
        return new NoOpEmrDocumentExtension();
    }

    /**
     * 病案首页上传 Extension 占位（chs-jilin 覆盖为 {@code MedcasHomepageUploadExtensionImpl}）。
     *
     * @return {@link MedcasHomepageUploadExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(MedcasHomepageUploadExtension.class)
    MedcasHomepageUploadExtension noOpMedcasHomepageUploadExtension() {
        return new NoOpMedcasHomepageUploadExtension();
    }

    /**
     * 医保手术目录源 Extension 占位（chs-jilin 覆盖为 {@code InsuranceSurgeryCatalogSourceExtensionImpl}）。
     *
     * @return {@link InsuranceSurgeryCatalogSourceExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(InsuranceSurgeryCatalogSourceExtension.class)
    InsuranceSurgeryCatalogSourceExtension noOpInsuranceSurgeryCatalogSourceExtension() {
        return new NoOpInsuranceSurgeryCatalogSourceExtension();
    }

    /**
     * 医保疾病/诊断目录源 Extension 占位（chs-jilin 覆盖为 {@code InsuranceDiseaseCatalogSourceExtensionImpl}）。
     *
     * @return {@link InsuranceDiseaseCatalogSourceExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(InsuranceDiseaseCatalogSourceExtension.class)
    InsuranceDiseaseCatalogSourceExtension noOpInsuranceDiseaseCatalogSourceExtension() {
        return new NoOpInsuranceDiseaseCatalogSourceExtension();
    }

    /**
     * 入院情况 EMR Extension 占位（EMR Module 覆盖为 {@code EmrAdmissionConditionExtensionImpl}）。
     *
     * @return {@link EmrAdmissionConditionExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(EmrAdmissionConditionExtension.class)
    EmrAdmissionConditionExtension noOpEmrAdmissionConditionExtension() {
        return new NoOpEmrAdmissionConditionExtension();
    }

    /**
     * LIS 检验申请 Extension 占位（LIS Module 覆盖为 {@code LisAdviceApplyExtensionImpl}）。
     *
     * @return {@link LisAdviceApplyExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(LisAdviceApplyExtension.class)
    LisAdviceApplyExtension noOpLisAdviceApplyExtension() {
        return new NoOpLisAdviceApplyExtension();
    }

    /**
     * PACS 检查申请 Extension 占位（PACS Module 覆盖为 {@code PacsAdviceApplyExtensionImpl}）。
     *
     * @return {@link PacsAdviceApplyExtension} 默认实现，{@code isEnabled()=false}
     */
    @Bean
    @ConditionalOnMissingBean(PacsAdviceApplyExtension.class)
    PacsAdviceApplyExtension noOpPacsAdviceApplyExtension() {
        return new NoOpPacsAdviceApplyExtension();
    }

    /**
     * 医护人员通知 Extension 默认实现（写日志；消息中心 Module 可 {@code @Primary} 覆盖）。
     *
     * @return {@link StaffNotificationExtension} 日志占位实现
     */
    @Bean
    @ConditionalOnMissingBean(StaffNotificationExtension.class)
    StaffNotificationExtension loggingStaffNotificationExtension() {
        return new LoggingStaffNotificationExtension();
    }
}
