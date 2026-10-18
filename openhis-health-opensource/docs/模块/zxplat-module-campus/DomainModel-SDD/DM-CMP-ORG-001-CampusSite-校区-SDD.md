# DM-CMP-ORG-001 CampusSite · DomainModel-SDD

> **对应 DM**：`DomainModel/DM-CMP-ORG-001-CampusSite-校区.md` v1.0  
> **目标工程**：`zxplat-module-campus/campus-domain`  
> **包**：`com.zxplat.plugin.campus.domain.org.campussite`

---

## 1. 设计目标与边界

- 固化校区 CRUD / 启停 / 设主校区；供学院、部门、档案、Peis Reader 引用。
- **在外**：菜单编排（AS-CMP-ORG-CMP-001）；多校区同步（P2）。

---

## 2. 包结构

| 相对包 | 内容 |
|--------|------|
| `entity` | `CampusSite` |
| `command` | `CreateCampusSiteCommand`、`UpdateCampusSiteCommand`、`ChangeCampusSiteActiveCommand`、`SetCampusSitePrimaryCommand` |
| `repository` | `CampusSiteRepository` |
| `service` | `CampusSiteDomainService` |
| `queries` | `CampusSiteQueryService` |
| `queries.model` | `CampusSiteQueryModel` |
| `event` | `CampusSiteUpsertedEvent`、`CampusSiteStatusChangedEvent` |

---

## 3. 技术口径

| 规则 | 说明 |
|------|------|
| 主键 | `Long id` |
| 时间 | `LocalDateTime` |
| 软删 | `Boolean isDeleted` |
| 仓储 | `JpaRepository`，禁止 `@Query`（复杂分页用 Specification / QueryDSL 按工程惯例） |
| 事务 | 领域服务不声明 `@Transactional` |

---

## 4. 仓储

```java
public interface CampusSiteRepository extends JpaRepository<CampusSite, Long> {
    Optional<CampusSite> findByTenantIdAndIdAndIsDeletedFalse(Long tenantId, Long id);
    Optional<CampusSite> findByTenantIdAndCampusCodeAndIsDeletedFalse(Long tenantId, String campusCode);
    boolean existsByTenantIdAndCampusCodeAndIsDeletedFalse(Long tenantId, String campusCode);
    List<CampusSite> findByTenantIdAndActiveFlagTrueAndIsDeletedFalseOrderBySortOrderAsc(Long tenantId);
}
```

---

## 5. 写方法契约

| 方法 | 校验 | 写库 | 事件 |
|------|------|------|------|
| create | BR-01/02；编码不存在 | insert | Upserted |
| update | 存在；编码不可变 | update | Upserted |
| changeActive | 存在 | update activeFlag | StatusChanged |
| setPrimary | 存在且启用 | 清其他 primary + 本条 true | Upserted |

---

## 6. QueryModel 字段

与 DM §3.1 对齐 + `id`。

---

## 7. 修订

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 成码首版 · PASS |
