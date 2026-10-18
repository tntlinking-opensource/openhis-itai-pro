# DM-CMP-ORG-003 CampusDepartment · DomainModel-SDD

> **对应 DM**：`DM-CMP-ORG-003` v1.0  
> **包**：`com.zxplat.plugin.campus.domain.org.campusdepartment`

---

## 1. 包结构

| 相对包 | 内容 |
|--------|------|
| `entity` | `CampusDepartment` |
| `command` | Create / Update / ChangeActive / Reparent |
| `repository` | `CampusDepartmentRepository` |
| `service` | `CampusDepartmentDomainService` |
| `queries` | `CampusDepartmentQueryService` + QueryModel + `DepartmentTreeNode` |

---

## 2. 仓储

```java
public interface CampusDepartmentRepository extends JpaRepository<CampusDepartment, Long> {
    Optional<CampusDepartment> findByTenantIdAndIdAndIsDeletedFalse(Long tenantId, Long id);
    boolean existsByTenantIdAndDepartmentCodeAndIsDeletedFalse(Long tenantId, String code);
    List<CampusDepartment> findByTenantIdAndCampusSiteIdAndIsDeletedFalse(Long tenantId, Long campusSiteId);
}
```

---

## 3. tree 读模型

`DepartmentTreeNode`：`type=SITE|COLLEGE|DEPT` + id/code/name/children。组装在 QueryService（可多查 Site/College/Dept）。

---

## 4. 修订

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | PASS |
