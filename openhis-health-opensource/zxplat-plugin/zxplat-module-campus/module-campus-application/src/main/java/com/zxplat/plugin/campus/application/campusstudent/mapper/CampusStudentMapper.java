/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusstudent.mapper;

import com.zxplat.plugin.campus.application.campusstudent.request.CreateStudentRequest;
import com.zxplat.plugin.campus.application.campusstudent.request.UpdateStudentRequest;
import com.zxplat.plugin.campus.application.campusstudent.response.CampusStudentDto;
import com.zxplat.plugin.campus.application.campusstudent.response.StudentCampusResponse;
import com.zxplat.plugin.campus.domain.archive.view.StudentCampusView;
import com.zxplat.plugin.campus.domain.patientstudent.entity.PatientStudent;
import org.springframework.stereotype.Component;

@Component
public class CampusStudentMapper {

    public CampusStudentDto toDto(PatientStudent entity) {
        CampusStudentDto dto = new CampusStudentDto();
        dto.setId(entity.getId());
        dto.setPatientId(entity.getPatientId());
        dto.setName(entity.getName());
        dto.setPyStr(entity.getPyStr());
        dto.setWbStr(entity.getWbStr());
        dto.setGenderEnum(entity.getGenderEnum());
        dto.setAge(entity.getAge());
        dto.setBirthday(entity.getBirthday());
        dto.setStudentNo(entity.getStudentNo());
        dto.setIdCard(entity.getIdCard());
        dto.setPhone(entity.getPhone());
        dto.setCollegeId(entity.getCollegeId());
        dto.setCollegeName(entity.getCollegeName());
        dto.setCampusCode(entity.getCampusCode());
        dto.setMajor(entity.getMajor());
        dto.setMajorCode(entity.getMajorCode());
        dto.setEduLevelEnum(entity.getEduLevelEnum());
        dto.setEnrollTime(entity.getEnrollTime());
        dto.setLeaveTime(entity.getLeaveTime());
        dto.setGrade(entity.getGrade());
        dto.setClassName(entity.getClassName());
        dto.setStudyYears(entity.getStudyYears());
        dto.setSchoolYear(entity.getSchoolYear());
        dto.setStudyTypeEnum(entity.getStudyTypeEnum());
        dto.setSchoolStatusEnum(entity.getSchoolStatusEnum());
        dto.setPhysicalExamResultEnum(entity.getPhysicalExamResultEnum());
        dto.setNativePlace(entity.getNativePlace());
        dto.setCounselorName(entity.getCounselorName());
        dto.setCounselorPhone(entity.getCounselorPhone());
        dto.setAddress(entity.getAddress());
        dto.setDoorplate(entity.getDoorplate());
        dto.setActiveFlag(entity.getActiveFlag());
        return dto;
    }

    public StudentCampusResponse toCampusResponse(StudentCampusView view) {
        StudentCampusResponse response = new StudentCampusResponse();
        response.setPatientId(view.getPatientId());
        response.setStudentId(view.getStudentId());
        response.setStudentNo(view.getStudentNo());
        response.setCampusCode(view.getCampusCode());
        response.setCollegeId(view.getCollegeId());
        response.setCollegeName(view.getCollegeName());
        return response;
    }

    public PatientStudent toEntity(CreateStudentRequest request) {
        PatientStudent entity = new PatientStudent();
        entity.setTenantId(request.getTenantId());
        entity.setName(request.getName());
        entity.setPyStr(request.getPyStr());
        entity.setWbStr(request.getWbStr());
        entity.setGenderEnum(request.getGenderEnum());
        entity.setAge(request.getAge());
        entity.setBirthday(request.getBirthday());
        entity.setStudentNo(request.getStudentNo());
        entity.setIdCard(request.getIdCard());
        entity.setPhone(request.getPhone());
        entity.setCollegeId(request.getCollegeId());
        entity.setCampusCode(request.getCampusCode());
        entity.setMajor(request.getMajor());
        entity.setMajorCode(request.getMajorCode());
        entity.setEduLevelEnum(request.getEduLevelEnum());
        entity.setEnrollTime(request.getEnrollTime());
        entity.setLeaveTime(request.getLeaveTime());
        entity.setGrade(request.getGrade());
        entity.setClassName(request.getClassName());
        entity.setStudyYears(request.getStudyYears());
        entity.setSchoolYear(request.getSchoolYear());
        entity.setStudyTypeEnum(request.getStudyTypeEnum());
        entity.setSchoolStatusEnum(request.getSchoolStatusEnum());
        entity.setPhysicalExamResultEnum(request.getPhysicalExamResultEnum());
        entity.setNativePlace(request.getNativePlace());
        entity.setCounselorName(request.getCounselorName());
        entity.setCounselorPhone(request.getCounselorPhone());
        entity.setAddress(request.getAddress());
        entity.setDoorplate(request.getDoorplate());
        entity.setActiveFlag(request.getActiveFlag());
        entity.setPatientId(request.getPatientId());
        return entity;
    }

    public PatientStudent toEntity(UpdateStudentRequest request) {
        PatientStudent entity = new PatientStudent();
        entity.setTenantId(request.getTenantId());
        entity.setId(request.getId());
        entity.setName(request.getName());
        entity.setPyStr(request.getPyStr());
        entity.setWbStr(request.getWbStr());
        entity.setGenderEnum(request.getGenderEnum());
        entity.setAge(request.getAge());
        entity.setBirthday(request.getBirthday());
        entity.setStudentNo(request.getStudentNo());
        entity.setIdCard(request.getIdCard());
        entity.setPhone(request.getPhone());
        entity.setCollegeId(request.getCollegeId());
        entity.setCampusCode(request.getCampusCode());
        entity.setMajor(request.getMajor());
        entity.setMajorCode(request.getMajorCode());
        entity.setEduLevelEnum(request.getEduLevelEnum());
        entity.setEnrollTime(request.getEnrollTime());
        entity.setLeaveTime(request.getLeaveTime());
        entity.setGrade(request.getGrade());
        entity.setClassName(request.getClassName());
        entity.setStudyYears(request.getStudyYears());
        entity.setSchoolYear(request.getSchoolYear());
        entity.setStudyTypeEnum(request.getStudyTypeEnum());
        entity.setSchoolStatusEnum(request.getSchoolStatusEnum());
        entity.setPhysicalExamResultEnum(request.getPhysicalExamResultEnum());
        entity.setNativePlace(request.getNativePlace());
        entity.setCounselorName(request.getCounselorName());
        entity.setCounselorPhone(request.getCounselorPhone());
        entity.setAddress(request.getAddress());
        entity.setDoorplate(request.getDoorplate());
        entity.setActiveFlag(request.getActiveFlag());
        return entity;
    }
}
