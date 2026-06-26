package com.openhis.whale.booking.adapter.reader;

import com.openhis.health.domain.administration.patient.queries.PatientPagedQuery;
import com.openhis.health.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.whale.booking.contracts.dto.BookingPatientItem;
import com.openhis.whale.booking.contracts.dto.BookingPatientPageResult;
import com.openhis.whale.booking.contracts.reader.HealthPatientReader;
import com.openhis.whale.booking.contracts.query.BookingPatientSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link HealthPatientReader} 实现：患者分页检索与姓名批量解析。
 *
 * <p>委托 health {@link PatientQueryService}；身份证号/手机号在 adapter 层脱敏。</p>
 */
@Component
@RequiredArgsConstructor
public class HealthPatientReaderAdapter implements HealthPatientReader {

    private final PatientQueryService patientQueryService;

    @Override
    public BookingPatientPageResult pagePatientsForSelect(BookingPatientSearchQuery query) {
        PatientPagedQuery patientQuery = new PatientPagedQuery();
        if (query != null) {
            patientQuery.setKeyword(query.getKeyword());
            patientQuery.setPageNo(query.getPageNo());
            patientQuery.setPageSize(query.getPageSize());
        }
        Page<PatientPageItemQueryModel> page = patientQueryService.searchPatientsPaged(patientQuery);

        BookingPatientPageResult result = new BookingPatientPageResult();
        List<BookingPatientItem> items = new ArrayList<>();
        for (PatientPageItemQueryModel row : page.getContent()) {
            if (row == null || row.getId() == null) {
                continue;
            }
            BookingPatientItem item = new BookingPatientItem();
            item.setPatientId(row.getId());
            item.setPatientBusNo(row.getBusNo());
            item.setName(row.getName());
            item.setGenderCode(row.getGenderEnum());
            item.setBirthDate(row.getBirthDate());
            // 敏感字段脱敏展示（非存储口径变更）。
            if (row.getIdCard() != null && row.getIdCard().length() > 4) {
                item.setIdCardNoMasked(row.getIdCard().substring(0, 2) + "****" + row.getIdCard().substring(row.getIdCard().length() - 2));
            }
            if (row.getPhone() != null && row.getPhone().length() > 4) {
                item.setPhoneMasked(row.getPhone().substring(0, 3) + "****" + row.getPhone().substring(row.getPhone().length() - 2));
            }
            items.add(item);
        }
        result.setContent(items);
        result.setTotalCount(page.getTotalElements());
        result.setPageNo(page.getNumber() + 1);
        result.setPageSize(page.getSize());
        return result;
    }

    @Override
    public Map<Long, String> findPatientNamesByIds(List<Long> patientIds) {
        return patientQueryService.findPatientNamesByIds(patientIds);
    }
}
