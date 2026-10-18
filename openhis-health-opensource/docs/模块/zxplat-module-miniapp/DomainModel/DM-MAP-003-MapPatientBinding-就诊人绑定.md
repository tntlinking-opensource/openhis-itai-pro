# DM-MAP-003 MapPatientBinding · 就诊人绑定

> **表名**：`map_patient_binding`  
> **版本**：v1.0 · **日期**：2026-09-24  
> **详细设计编号**：DM-MAP-003-MapPatientBinding

---

## 1. 概述

微信账号 ↔ HIS `Patient` 多对多绑定；支持默认就诊人与校园元数据（student/staff 校验结果落库，不新建 peis_customer）。

## 2. 持久化形态

| 列 | 类型 | 说明 |
|----|------|------|
| id | int8 | PK |
| tenant_id | int8 | |
| wechat_account_id | int8 | |
| patient_id | int8 | FK → adm_patient |
| bind_channel_enum | varchar(32) | manual / campus_student / campus_staff … |
| campus_ref_type / campus_ref_id / campus_biz_no | | 校园溯源 |
| is_default | bool | |
| bound_at / unbound_at | timestamptz | |

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `MapPatientBinding` |
| **写侧** | `MapAuthDomainService.bindPatient` |

医护侧对称实体：`MapPractitionerBinding`（L1 见 [practitionerbinding](./practitionerbinding/)）。
