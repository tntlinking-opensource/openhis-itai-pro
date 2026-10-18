# AS-PACS-CFG-004 系统参数 · SDD

> **版本**：v1.0 · **日期**：2026-09-21 · **对应 AS** CFG-004 v1.0

## 1. 边界

`PacsSystemConfigAppService#getSystemParams`；注入 `ObjectProvider<PacsAdapterProperties>`（未启用 adapter 时返回默认值）。

## 2. HTTP

`POST /api/app/pacsSystemConfig/getSystemParams`

## 3. 修订

| v1.0 | 2026-09-21 | 首版 |
