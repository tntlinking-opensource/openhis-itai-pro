# AS-CMP-ORG-CMP-001 校区管理 · SDD

> **对应 AS**：v1.0 · **类**：`CampusSiteAppService`  
> **包**：`com.zxplat.plugin.campus.application.org.campussite`

---

## 1. 代码结构

```
campus-application/.../org/campussite/
├── CampusSiteAppService.java
├── request/
├── response/
└── mapper/
```

HTTP：`campusSite` → `/api/app/campusSite/{method}`

## 2. 方法映射

| IF | 方法 | 领域 |
|----|------|------|
| CMP-01 | pageCampusSites | Query#page |
| CMP-02 | createCampusSite / updateCampusSite | Domain#create/update |
| CMP-03 | changeCampusSiteActive | Domain#changeActive |
| CMP-07 | listActiveCampusSites | Query#listActive |
| CMP-08 | resolveCampusByCode | Query#getByCode |
| — | setPrimaryCampusSite | Domain#setPrimary |

## 3. 修订

| v1.0 | 2026-07-19 | PASS |
