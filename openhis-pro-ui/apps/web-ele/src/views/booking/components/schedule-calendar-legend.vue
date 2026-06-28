<template>
  <div class="schedule-calendar-legend" aria-label="周历图例">
    <span class="schedule-calendar-legend__item">
      <i class="schedule-calendar-legend__swatch is-template" />
      <span>
        预览
        <span class="schedule-calendar-legend__sub">（仅有惯例，尚未生成）</span>
      </span>
    </span>
    <span class="schedule-calendar-legend__item">
      <i class="schedule-calendar-legend__swatch is-draft" />
      <span>
        草稿
        <span class="schedule-calendar-legend__sub">（须发布）</span>
      </span>
    </span>
    <span class="schedule-calendar-legend__item">
      <i class="schedule-calendar-legend__swatch is-active" />
      <span>
        已发布
        <span class="schedule-calendar-legend__sub">（须放号后可约）</span>
      </span>
    </span>
    <span class="schedule-calendar-legend__item">
      <i class="schedule-calendar-legend__swatch is-inactive" />
      <span>停诊</span>
    </span>
  </div>
</template>

<style scoped>
.schedule-calendar-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
  align-items: center;
  padding: 6px 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.schedule-calendar-legend__item {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.schedule-calendar-legend__sub {
  color: var(--el-text-color-placeholder);
}

.schedule-calendar-legend__swatch {
  display: inline-block;
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  border: 1px solid transparent;
  border-radius: 4px;
}

.schedule-calendar-legend__swatch.is-template {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
  border-style: dashed;
}

.schedule-calendar-legend__swatch.is-draft {
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color);
}

.schedule-calendar-legend__swatch.is-active {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-5);
}

.schedule-calendar-legend__swatch.is-inactive {
  background: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger-light-5);
  opacity: 0.85;
}
</style>
