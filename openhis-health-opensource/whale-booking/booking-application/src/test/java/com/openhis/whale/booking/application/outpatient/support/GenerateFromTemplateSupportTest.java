package com.openhis.whale.booking.application.outpatient.support;

import com.openhis.whale.booking.application.outpatient.mapper.OutpatientScheduleMapper;
import com.openhis.whale.booking.application.outpatient.request.GenerateFromTemplateRequest;
import com.openhis.whale.booking.application.outpatient.response.GenerateFromTemplateResponse;
import com.openhis.whale.booking.application.support.BookingAuditSupport;
import com.openhis.whale.booking.application.support.BookingOperatorResolver;
import com.openhis.whale.booking.application.support.ScheduleActorMasterDataValidator;
import com.openhis.whale.booking.contracts.dto.MasterDataOption;
import com.openhis.whale.booking.contracts.dto.ScheduleFormMasterDataSnapshot;
import com.openhis.whale.booking.contracts.reader.HealthScheduleMasterDataReader;
import com.openhis.whale.booking.domain.calendarexception.queries.model.DayGeneratePlanModel;
import com.openhis.whale.booking.domain.calendarexception.service.CalendarExceptionQueryService;
import com.openhis.whale.booking.domain.policy.service.PolicyQueryService;
import com.openhis.whale.booking.domain.schedule.service.ScheduleDomainService;
import com.openhis.whale.booking.domain.slot.service.SlotDomainService;
import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.service.TemplateQueryService;
import com.openhis.whale.booking.domain.template.service.WeekScheduleTemplateQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TC-TPL-01/02：从模板生成与 T3 日历例外联调验收。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenerateFromTemplateSupportTest {

    private static final Long ORG_ID = 100L;
    private static final Long BASE_TEMPLATE_ID = 10L;
    private static final Long EXTRA_TEMPLATE_ID = 20L;
    private static final Long OPERATOR_ID = 1L;

    @Mock
    private TemplateQueryService templateQueryService;
    @Mock
    private WeekScheduleTemplateQueryService weekScheduleTemplateQueryService;
    @Mock
    private CalendarExceptionQueryService calendarExceptionQueryService;
    @Mock
    private PolicyQueryService policyQueryService;
    @Mock
    private ScheduleDomainService scheduleDomainService;
    @Mock
    private SlotDomainService slotDomainService;

    private GenerateFromTemplateSupport support;

    private ScheduleTemplate baseTemplate;
    private ScheduleTemplate extraTemplate;

    @BeforeEach
    void setUp() {
        HealthScheduleMasterDataReader healthScheduleMasterDataReader = organizationId -> {
            ScheduleFormMasterDataSnapshot snapshot = new ScheduleFormMasterDataSnapshot();
            snapshot.setDepartments(List.of(option(organizationId)));
            snapshot.setHealthcareServices(List.of(option(900L)));
            return snapshot;
        };
        ScheduleActorMasterDataValidator actorValidator = new ScheduleActorMasterDataValidator(healthScheduleMasterDataReader);
        BookingAuditSupport auditSupport = new BookingAuditSupport(new BookingOperatorResolver(null));

        support = new GenerateFromTemplateSupport(
            templateQueryService,
            weekScheduleTemplateQueryService,
            calendarExceptionQueryService,
            policyQueryService,
            scheduleDomainService,
            slotDomainService,
            new OutpatientScheduleMapper(),
            actorValidator,
            auditSupport);

        baseTemplate = usableTemplate(BASE_TEMPLATE_ID, ORG_ID);
        extraTemplate = usableTemplate(EXTRA_TEMPLATE_ID, ORG_ID);
        when(templateQueryService.loadScheduleTemplateById(BASE_TEMPLATE_ID)).thenReturn(baseTemplate);
        when(templateQueryService.loadScheduleTemplateById(EXTRA_TEMPLATE_ID)).thenReturn(extraTemplate);
        when(scheduleDomainService.createSchedule(any())).thenReturn(1001L, 1002L, 1003L);
    }

    @Test
    void generate_closedDay_skipsNormalGeneration_tcTpl01() {
        LocalDate closedDay = LocalDate.of(2026, 10, 1);
        when(calendarExceptionQueryService.resolveDayGeneratePlan(ORG_ID, closedDay)).thenReturn(closedPlan());

        GenerateFromTemplateRequest request = requestFor(closedDay, closedDay);

        assertThatThrownBy(() -> support.generate(request, OPERATOR_ID))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("T3 日历例外");

        verify(scheduleDomainService, never()).createSchedule(any());
    }

    @Test
    void generate_mixedRange_skipsClosedOnly_tcTpl01() {
        LocalDate closedDay = LocalDate.of(2026, 10, 1);
        LocalDate normalDay = LocalDate.of(2026, 10, 2);
        when(calendarExceptionQueryService.resolveDayGeneratePlan(ORG_ID, closedDay)).thenReturn(closedPlan());
        when(calendarExceptionQueryService.resolveDayGeneratePlan(ORG_ID, normalDay)).thenReturn(normalPlan());

        GenerateFromTemplateResponse response = support.generate(requestFor(closedDay, normalDay), OPERATOR_ID);

        assertThat(response.getGeneratedCount()).isEqualTo(1);
        assertThat(response.getSkippedCount()).isEqualTo(1);
        verify(scheduleDomainService, times(1)).createSchedule(any());
    }

    @Test
    void generate_extraDay_createsAdditionalSchedule_tcTpl02() {
        LocalDate extraDay = LocalDate.of(2026, 10, 6);
        when(calendarExceptionQueryService.resolveDayGeneratePlan(ORG_ID, extraDay)).thenReturn(extraPlan());

        GenerateFromTemplateResponse response = support.generate(requestFor(extraDay, extraDay), OPERATOR_ID);

        assertThat(response.getGeneratedCount()).isEqualTo(2);
        assertThat(response.getSkippedCount()).isZero();
        verify(scheduleDomainService, times(2)).createSchedule(any());
    }

    private static MasterDataOption option(Long id) {
        MasterDataOption option = new MasterDataOption();
        option.setValue(String.valueOf(id));
        option.setLabel("opt-" + id);
        return option;
    }

    private static GenerateFromTemplateRequest requestFor(LocalDate start, LocalDate end) {
        GenerateFromTemplateRequest request = new GenerateFromTemplateRequest();
        request.setOrganizationId(ORG_ID);
        request.setScheduleTemplateId(BASE_TEMPLATE_ID);
        request.setRangeStart(start);
        request.setRangeEnd(end);
        request.setAutoPublish(Boolean.FALSE);
        return request;
    }

    private static ScheduleTemplate usableTemplate(Long id, Long organizationId) {
        ScheduleTemplate template = new ScheduleTemplate();
        template.setId(id);
        template.setOrganizationId(organizationId);
        template.setTemplateCode("TPL_" + id);
        template.setTemplateName("模板" + id);
        template.setScheduleMode("MODE-A");
        template.setHealthcareServiceId(900L);
        template.setEnabled(Boolean.TRUE);
        return template;
    }

    private static DayGeneratePlanModel closedPlan() {
        DayGeneratePlanModel plan = new DayGeneratePlanModel();
        plan.setSkipNormal(true);
        return plan;
    }

    private static DayGeneratePlanModel normalPlan() {
        return new DayGeneratePlanModel();
    }

    private static DayGeneratePlanModel extraPlan() {
        DayGeneratePlanModel plan = new DayGeneratePlanModel();
        plan.setGenerateExtra(true);
        plan.setExtraTemplateId(EXTRA_TEMPLATE_ID);
        return plan;
    }
}
