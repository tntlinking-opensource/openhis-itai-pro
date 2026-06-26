/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.support.config;

import com.openhis.health.integration.booking.outpatient.OutpatientBookingQueryExtension;
import com.openhis.health.integration.booking.outpatient.OutpatientBookingRegistrationExtension;
import com.openhis.health.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.openhis.health.integration.insurance.inpatient.InpatientChargeInsuranceExtension;
import com.openhis.health.integration.insurance.inventory.DispenseTraceGateExtension;
import com.openhis.health.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.openhis.health.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;
import com.openhis.health.integration.insurance.platformdict.InsurancePlatformDictDisplayExtension;
import com.openhis.health.integration.emr.EmrAdmissionDiagnosisSyncExtension;
import com.openhis.health.integration.einvoice.BossElectronicInvoiceExtension;
import com.openhis.health.integration.elep.ElepPrescriptionExtension;
import com.openhis.health.integration.mrms.MrmsCaseRecordBootstrapExtension;
import com.openhis.health.integration.payment.ThirdPartyPaymentExtension;
import com.openhis.health.integration.support.booking.NoOpOutpatientBookingQueryExtension;
import com.openhis.health.integration.support.booking.NoOpOutpatientBookingRegistrationExtension;
import com.openhis.health.integration.support.emr.NoOpEmrAdmissionDiagnosisSyncExtension;
import com.openhis.health.integration.support.einvoice.NoOpBossElectronicInvoiceExtension;
import com.openhis.health.integration.support.insurance.NoOpInsurancePlatformDictDisplayExtension;
import com.openhis.health.integration.support.insurance.benefit.NoOpInsuranceRegisterBenefitQueryExtension;
import com.openhis.health.integration.support.insurance.inpatient.NoOpInpatientChargeInsuranceExtension;
import com.openhis.health.integration.support.insurance.inventory.NoOpDispenseTraceGateExtension;
import com.openhis.health.integration.support.insurance.inventory.NoOpInventoryThirdPartyUploadExtension;
import com.openhis.health.integration.support.insurance.outpatient.NoOpOutpatientChargeInsuranceExtension;
import com.openhis.health.integration.support.insurance.outpatient.NoOpOutpatientRegistrationInsuranceExtension;
import com.openhis.health.integration.support.elep.NoOpElepPrescriptionExtension;
import com.openhis.health.integration.support.mrms.NoOpMrmsCaseRecordBootstrapExtension;
import com.openhis.health.integration.support.payment.NoOpThirdPartyPaymentExtension;
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
public class HealthIntegrationContractsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(InsurancePlatformDictDisplayExtension.class)
    InsurancePlatformDictDisplayExtension noOpInsurancePlatformDictDisplayExtension() {
        return new NoOpInsurancePlatformDictDisplayExtension();
    }

    /** 门诊收银医保 Extension 占位（吉林 Module 覆盖为 {@code OutpatientChargeInsuranceExtensionImpl}）。 */
    @Bean
    @ConditionalOnMissingBean(OutpatientChargeInsuranceExtension.class)
    OutpatientChargeInsuranceExtension noOpOutpatientChargeInsuranceExtension() {
        return new NoOpOutpatientChargeInsuranceExtension();
    }

    /** 门诊挂号 2201 Extension 占位（吉林覆盖为 {@code OutpatientRegistrationInsuranceExtensionImpl}）。 */
    @Bean
    @ConditionalOnMissingBean(OutpatientRegistrationInsuranceExtension.class)
    OutpatientRegistrationInsuranceExtension noOpOutpatientRegistrationInsuranceExtension() {
        return new NoOpOutpatientRegistrationInsuranceExtension();
    }

    /** 住院结算 Extension 占位（吉林覆盖为 {@code InpatientChargeInsuranceExtensionImpl}）。 */
    @Bean
    @ConditionalOnMissingBean(InpatientChargeInsuranceExtension.class)
    InpatientChargeInsuranceExtension noOpInpatientChargeInsuranceExtension() {
        return new NoOpInpatientChargeInsuranceExtension();
    }

    /** 进销存上报 Extension 占位（吉林覆盖为 {@code InventoryThirdPartyUploadExtensionImpl}）。 */
    @Bean
    @ConditionalOnMissingBean(InventoryThirdPartyUploadExtension.class)
    InventoryThirdPartyUploadExtension noOpInventoryThirdPartyUploadExtension() {
        return new NoOpInventoryThirdPartyUploadExtension();
    }

    /** 发药追溯门控 Extension 占位（吉林覆盖为 {@code DispenseTraceGateExtensionImpl}）。 */
    @Bean
    @ConditionalOnMissingBean(DispenseTraceGateExtension.class)
    DispenseTraceGateExtension noOpDispenseTraceGateExtension() {
        return new NoOpDispenseTraceGateExtension();
    }

    @Bean
    @ConditionalOnMissingBean(ElepPrescriptionExtension.class)
    ElepPrescriptionExtension noOpElepPrescriptionExtension() {
        return new NoOpElepPrescriptionExtension();
    }

    /** 挂号待遇查询 Extension 占位（吉林覆盖为 {@code InsuranceRegisterBenefitQueryExtensionImpl}）。 */
    @Bean
    @ConditionalOnMissingBean(InsuranceRegisterBenefitQueryExtension.class)
    InsuranceRegisterBenefitQueryExtension noOpInsuranceRegisterBenefitQueryExtension() {
        return new NoOpInsuranceRegisterBenefitQueryExtension();
    }

    @Bean
    @ConditionalOnMissingBean(EmrAdmissionDiagnosisSyncExtension.class)
    EmrAdmissionDiagnosisSyncExtension noOpEmrAdmissionDiagnosisSyncExtension() {
        return new NoOpEmrAdmissionDiagnosisSyncExtension();
    }

    @Bean
    @ConditionalOnMissingBean(OutpatientBookingQueryExtension.class)
    OutpatientBookingQueryExtension noOpOutpatientBookingQueryExtension() {
        return new NoOpOutpatientBookingQueryExtension();
    }

    @Bean
    @ConditionalOnMissingBean(OutpatientBookingRegistrationExtension.class)
    OutpatientBookingRegistrationExtension noOpOutpatientBookingRegistrationExtension() {
        return new NoOpOutpatientBookingRegistrationExtension();
    }

    @Bean
    @ConditionalOnMissingBean(MrmsCaseRecordBootstrapExtension.class)
    MrmsCaseRecordBootstrapExtension noOpMrmsCaseRecordBootstrapExtension() {
        return new NoOpMrmsCaseRecordBootstrapExtension();
    }

    @Bean
    @ConditionalOnMissingBean(BossElectronicInvoiceExtension.class)
    BossElectronicInvoiceExtension noOpBossElectronicInvoiceExtension() {
        return new NoOpBossElectronicInvoiceExtension();
    }

    @Bean
    @ConditionalOnMissingBean(ThirdPartyPaymentExtension.class)
    ThirdPartyPaymentExtension noOpThirdPartyPaymentExtension() {
        return new NoOpThirdPartyPaymentExtension();
    }
}
