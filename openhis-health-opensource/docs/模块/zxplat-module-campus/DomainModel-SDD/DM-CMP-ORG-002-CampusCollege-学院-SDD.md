# DM-CMP-ORG-002 CampusCollege · DomainModel-SDD

> **对应 DM**：`DM-CMP-ORG-002` v1.0  
> **包**：`com.zxplat.plugin.campus.domain.org.campuscollege`

---

## 1. 边界

学院主数据；绑定可选校区；Peis `listActiveColleges` 读侧。

---

## 2. 包结构

| 相对包 | 内容 |
|--------|------|
| `entity` | `CampusCollege` |
| `command` | `CreateCampusCollegeCommand`、`UpdateCampusCollegeCommand`、`ChangeCampusCollegeActiveCommand`、`BindCollegeCampusCommand` |
| `repository` | `CampusCollegeRepository` |
| `service` | `CampusCollegeDomainService` |
| `queries` / `queries.model` | `CampusCollegeQueryService`、`CampusCollegeQueryModel` |
| `event` | Upserted / StatusChanged |

---

## 3. 仓储

```java
public interface CampusCollegeRepository extends JpaRepository<CampusCollege, Long> {
    Optional<CampusCollege> findByTenantIdAndIdAndIsDeletedFalse(Long tenantId, Long id);
    boolean existsByTenantIdAndCollegeCodeAndIsDeletedFalse(Long tenantId, String collegeCode);
    List<CampusCollege> findByTenantIdAndActiveFlagTrueAndIsDeletedFalse(Long tenantId);
}
```

跨聚合读校区启用态：经 `CampusSiteQueryService`（领域内可注入 Query，禁止直接改 Site）。

---

## 4. 写方法

| 方法 | 要点 |
|------|------|
| create/update | BR-COL-01/02；名称必填 |
| changeActive | 停用不级联删学生 |
| bindCampus | campusSiteId 可 null |

---

## 5. 修订

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | PASS |
