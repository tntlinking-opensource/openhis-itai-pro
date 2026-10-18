# AS-CMP-ORG-COL-001 学院管理 · SDD

> **包**：`com.zxplat.plugin.campus.application.org.campuscollege` · **类**：`CampusCollegeAppService`

| IF | 方法 | 领域 |
|----|------|------|
| COL-01 | pageColleges | Query#page |
| COL-02 | createCollege / updateCollege | Domain#create/update |
| COL-03 | changeCollegeActive | Domain#changeActive |
| COL-04 | bindCollegeCampus | Domain#bindCampus |
| COL-08 | listActiveColleges | Query#listActive |
| COL-09 | getCollegeById | Query#getById |

Reader 实现委托本 AS 的 Query 或直接 Domain Query（推荐 Reader → QueryService，避免 HTTP 自调用）。

| v1.0 | 2026-07-19 | PASS |
