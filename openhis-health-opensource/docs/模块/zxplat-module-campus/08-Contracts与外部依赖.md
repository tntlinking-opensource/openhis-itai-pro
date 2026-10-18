# Campus · Contracts 与外部依赖

> **版本**：v1.3 · **日期**：2026-07-19  
> **工程**：`module-campus-domain`（接口）+ `module-campus-application`（实现）  
> **包**：`com.zxplat.plugin.campus.domain.*`（Reader 接口 / View）；实现类 `com.zxplat.plugin.campus.application.*`  
> **原则**：Peis / health 收银 **只读** Campus；禁止反依赖 `module-campus-application`（可依赖 domain 接口）  
> **v1.3**：v3 无独立 `*-contracts` 模块（对齐模块代码规范）

---

## 1. Reader 清单（P0）

| 接口 | 消费者 | 对应 IF | 状态 |
|------|--------|---------|:--:|
| `CampusOrgQueryReader` | Peis GRP-01、登记下拉 | INT-04、COL-08、CMP-07/08、DEP-08 | ✅ Batch A |
| `CampusArchiveQueryReader` | Peis 名册、BAS | STU-17、ARCH-07 | ✅ Batch B |
| `CampusPublicExpenseEligibilityReader` | health 收银前校验 | ARCH-05/06 | ✅ Batch B |
| `CampusChargeCalcReader` | health 收银拆分 | INT-03、CHG-07 | ✅ 试算；改草稿 ⬜ D |

实现类经 starter 暴露 Spring Bean；health/Peis 用 `ObjectProvider<*Reader>` 可选注入。

---

## 2. 接口签名（目标态）

### 2.1 `CampusOrgQueryReader`

```java
public interface CampusOrgQueryReader {
    List<CampusSiteView> listActiveCampusSites(Long tenantId);
    Optional<CampusSiteView> resolveCampusByCode(Long tenantId, String campusCode);
    List<CampusCollegeView> listActiveColleges(Long tenantId, String campusCodeOrNull);
    Optional<CampusCollegeView> getCollegeById(Long tenantId, Long collegeId);
    List<CampusDepartmentView> listDepartments(Long tenantId, Long collegeIdOrNull, String campusCodeOrNull);
}
```

**View 最小字段**

| View | 字段 |
|------|------|
| CampusSiteView | id, campusCode, campusName, shortName, isPrimary |
| CampusCollegeView | id, collegeCode, collegeName, campusSiteId, campusCode |
| CampusDepartmentView | id, departmentCode, departmentName, campusSiteId, collegeId |

### 2.2 `CampusArchiveQueryReader`

```java
public interface CampusArchiveQueryReader {
    Optional<StudentCampusView> resolveStudentCampus(Long tenantId, Long patientId);
    Optional<StudentCampusView> resolveStudentCampusByStudentNo(Long tenantId, String studentNo);
    Optional<CampusProfileView> resolveCampusProfileByPatientId(Long tenantId, Long patientId);
    List<ArchiveTransferView> listTransfers(Long tenantId, String subjectType, Long subjectId);
}
```

| View | 字段 |
|------|------|
| StudentCampusView | patientId, studentId, studentNo, campusCode, collegeId, collegeName |
| CampusProfileView | subjectType, subjectId, cardNo, gfStatusEnum, medicalCode, campusCode, departmentId?, collegeId? |
| ArchiveTransferView | id, transferType, effectiveDate, from/to 校区·学院或部门摘要, reason, operatorName |

### 2.3 `CampusPublicExpenseEligibilityReader`

```java
public interface CampusPublicExpenseEligibilityReader {
    EligibilityView check(Long tenantId, Long patientId);
}
```

字段对齐 ARCH-05 Response（passed / reasonCode / reasonMessage / gfStatusEnum / cardNo）。  
**注意**：`cardNo` ≠ `Patient.busNo`（院内编码）。

### 2.4 `CampusChargeCalcReader`

```java
public interface CampusChargeCalcReader {
    PublicExpenseSplitView previewSplit(Long tenantId, PublicExpenseSplitQuery query);
}
```

query/view 对齐 [AS-CMP-GF-CALC-001](./AppService/AS-CMP-GF-CALC-001-收费试算.md)。

---

## 3. 未启用 Campus 时的降级

| 场景 | 行为 |
|------|------|
| `zxplat.modules.campus.enabled=false` | contracts **无实现 Bean**；Peis/收银不得强依赖 |
| Peis | 学院下拉 **空列表**（禁止再读/写 `peis_company_college` 当真源） |
| 收银 | 跳过公费拆分；不注入 ChargeCalcReader |
| 挂号开户 | 不开 `PUBLIC_EXPENSE_ACCOUNT`（ADR D12） |

推荐：health/Peis 使用 `ObjectProvider<CampusOrgQueryReader>` 等可选注入。

### 3.1 公费账户（ADR D12 · health）

目标态新增 `AccountTypeCode.PUBLIC_EXPENSE_ACCOUNT`。  
挂号：Campus 启用且 `CampusArchiveQueryReader` 命中**学生** → 幂等开户；教职工收费选 `gfChargeMode` 时再处理。  
支付明细：公费行挂公费户，自付行挂 `PERSONAL_CASH_ACCOUNT`。

---

## 4. 外部系统

| 系统 | 关系 |
|------|------|
| health Patient/ADM | 学生写侧；Patient 绑定；挂号/预结算挂接 |
| PeisAppService | 读组织/名册；P2 回写体检态 |
| 教务/人事 | P3 INT-07/08 |

> 首批审批 **不依赖** nenu Flowable（ADR D8）。

---

## 5. 修订

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 成码首版 Reader 契约 |
| v1.1 | 2026-07-19 | Profile/Eligibility 增加 cardNo、gfStatusEnum |
| v1.2 | 2026-07-19 | ADR-CMP-001：严格降级、公费账户、无 Flowable |
| v1.3 | 2026-07-19 | Batch B Reader 落地；接口改落 domain（无 contracts 模块） |
