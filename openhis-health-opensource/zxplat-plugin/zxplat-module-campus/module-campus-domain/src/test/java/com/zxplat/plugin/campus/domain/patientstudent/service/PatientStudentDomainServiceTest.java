/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.patientstudent.service;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.campus.domain.campuscollege.repository.CampusCollegeRepository;
import com.zxplat.plugin.campus.domain.campussite.repository.CampusSiteRepository;
import com.zxplat.plugin.campus.domain.patientstudent.entity.PatientStudent;
import com.zxplat.plugin.campus.domain.patientstudent.repository.PatientStudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PatientStudentDomainService 关键：学院必填、学号唯一、绑定患者冲突。
 */
@ExtendWith(MockitoExtension.class)
class PatientStudentDomainServiceTest {

    @Mock
    private PatientStudentRepository repository;
    @Mock
    private CampusCollegeRepository collegeRepository;
    @Mock
    private CampusSiteRepository campusSiteRepository;

    @InjectMocks
    private PatientStudentDomainService patientStudentDomainService;

    @Test
    void create_rejectsMissingCollege() {
        PatientStudent student = new PatientStudent();
        student.setTenantId(1L);
        student.setName("王五");
        student.setStudentNo("S001");
        student.setCampusCode("MAIN");

        when(repository.findByTenantIdAndStudentNoAndIsDeletedFalse(1L, "S001"))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> patientStudentDomainService.create(student, 99L));
        assertEquals("COLLEGE_NOT_FOUND", ex.getErrorCode());
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsDuplicateStudentNo() {
        PatientStudent existing = new PatientStudent();
        existing.setId(10L);
        when(repository.findByTenantIdAndStudentNoAndIsDeletedFalse(1L, "S001"))
                .thenReturn(Optional.of(existing));

        PatientStudent student = new PatientStudent();
        student.setTenantId(1L);
        student.setStudentNo("S001");
        student.setCollegeId(2L);
        student.setCampusCode("MAIN");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> patientStudentDomainService.create(student, 99L));
        assertEquals("STUDENT_NO_DUPLICATE", ex.getErrorCode());
        verify(repository, never()).save(any());
    }

    @Test
    void bindPatient_rejectsNullPatientId() {
        PatientStudent existing = new PatientStudent();
        existing.setId(10L);
        existing.setTenantId(1L);
        when(repository.findByIdAndTenantIdAndIsDeletedFalse(10L, 1L))
                .thenReturn(Optional.of(existing));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> patientStudentDomainService.bindPatient(1L, 10L, null, 99L));
        assertEquals("PATIENT_ID_REQUIRED", ex.getErrorCode());
    }
}
