<!--
  DrugOrderGroupCell — 医嘱/处方「成组括线」版式壳，与具体业务字段解耦。

  【职责】只负责布局与括线样式；药品名、规格、剂量、用法等全部由插槽由调用方渲染。

  【items 与分支】
  - items.length > 1：成组多药，左侧括线 + 每行 #line + 底部 #shared
  - items.length === 1：单条结构化，#main（主行）+ #shared
  - 无 items 或 length === 0：#fallback（如整行纯文本医嘱）

  ---------------------------------------------------------------------------
  示例 1 — 医生站成组（items 为多条，字段名由页面自定，此处仅作 JSON 示意）：

  [
    {
      "selectedDrugName": "0.9%氯化钠注射液",
      "selectedDrugSpec": "250ml/瓶",
      "pharmacy": "住院西药房",
      "singleDose": 1,
      "doseSubUnit": "瓶",
      "totalAmount": 1,
      "totalAmountUnit": "瓶",
      "usage": "静滴",
      "frequency": "每日一次",
      "medicationDays": 1
    },
    {
      "selectedDrugName": "注射用头孢呋辛钠",
      "selectedDrugSpec": "0.75g/支",
      "pharmacy": "住院西药房",
      "singleDose": 1,
      "doseSubUnit": "支",
      "totalAmount": 1,
      "totalAmountUnit": "支",
      "usage": "静滴",
      "frequency": "每日一次",
      "medicationDays": 1
    }
  ]

  另一页面若接口字段不同，仍可把 items 设为：

  [
    { "drugName": "维生素C片", "specText": "0.1g*100片", "storeName": "门诊药房", ... }
  ]

  只要在 #line / #shared / #main 里按该结构取值即可。

  ---------------------------------------------------------------------------
  示例 2 — 接口按「每条一行药品」返回、先合并再塞进 items（见 #/utils/doctor-station/drug-group-table）：

  原始行（连续相同 groupId 会被合并为一行）：

  [
    { "groupId": "G01", "drugName": "药品A", "onceDose": "1", "unit": "支", ... },
    { "groupId": "G01", "drugName": "药品B", ... },
    { "groupId": "", "drugName": "单项C", ... }
  ]

  合并后表格行 data 上 items 为前两条组成的数组，本组件 items.length===2 走成组展示。

  ---------------------------------------------------------------------------
  示例 3 — 无结构化 items（纯文本医嘱），items 传 undefined 或 []，使用 #fallback：

  在 #fallback 内自行渲染，例如一行主名 + 一行剂量 + 一行用法（字段来自 row.content / row.dosageText 等）。
-->
<script setup lang="ts">
import { computed } from 'vue';

defineOptions({ name: 'DrugOrderGroupCell' });

const props = withDefaults(
  defineProps<{
    /**
     * 本行药品列表；元素类型由业务自定，组件仅做长度分支与透传给插槽。
     * 见文件顶部注释中的示例 JSON。
     */
    items?: null | readonly unknown[];
    /** 表格行主键，用于组内 v-for 的 :key 前缀 */
    rowKey: string;
  }>(),
  {
    items: undefined,
  },
);

/**
 * 统一成数组，避免模板中反复判空。
 * 注意：必须同时排除 undefined 与 null；若只判断 null，row.groupItems 未定义时会执行 `[...undefined]` 抛错，#fallback 无法渲染（不成组行空白）。
 */
const normalizedItems = computed(() => {
  const raw = props.items;
  if (raw === undefined || raw === null) {
    return [];
  }
  return [...raw];
});
</script>

<template>
  <!-- 成组：多行 #line + 共用 #shared -->
  <div v-if="normalizedItems.length > 1" class="group-content-cell">
    <div class="group-drug-list">
      <div v-for="(item, idx) in normalizedItems" :key="`${rowKey}-${idx}`" class="group-drug-line">
        <slot name="line" :item="item" :index="idx"></slot>
      </div>
    </div>
    <div class="group-drug-shared">
      <slot name="shared" :items="normalizedItems"></slot>
    </div>
  </div>

  <!-- 单条结构化：#main + #shared -->
  <div v-else-if="normalizedItems.length === 1" class="normal-content-cell">
    <div class="drug-main-row">
      <slot name="main" :item="normalizedItems[0]!"></slot>
    </div>
    <div class="normal-drug-shared">
      <slot name="shared" :items="normalizedItems"></slot>
    </div>
  </div>

  <!-- 无 items：整格交给调用方（建议在 #fallback 内使用 class drug-main-text / normal-drug-shared，见下方 :deep 样式） -->
  <div v-else class="normal-content-cell normal-content-cell--fallback">
    <slot name="fallback"></slot>
  </div>
</template>

<style scoped>
.group-content-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.group-drug-list {
  position: relative;
  line-height: 1.4;
  padding-left: 12px;
  padding-bottom: 4px;
  border-bottom: 1px dashed #cbd5e1;
}

.group-drug-list::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 0;
  border-left: 1px solid #94a3b8;
}

.group-drug-list::after {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  width: 10px;
  border-top: 1px solid #94a3b8;
}

.group-drug-line:last-child::after {
  content: '';
  position: absolute;
  left: -12px;
  bottom: -5px;
  width: 10px;
  border-bottom: 1px solid #94a3b8;
}

.group-drug-line {
  display: flex;
  align-items: baseline;
  width: 100%;
  min-width: 0;
  position: relative;
}

/* 成组每行：建议在 #line 内使用 class="drug-main-text" / "group-drug-dose" */
.group-drug-line :deep(.drug-main-text) {
  color: #111827;
  font-size: 14px;
  font-weight: 500;
}

.group-drug-line :deep(.group-drug-dose) {
  color: #475569;
  font-size: 12px;
}

.drug-main-row {
  width: 100%;
  min-width: 0;
}

.drug-main-row :deep(.drug-main-text) {
  color: #111827;
  font-size: 14px;
  font-weight: 500;
}

.drug-main-row :deep(.group-drug-dose) {
  color: #475569;
  font-size: 12px;
}

.drug-main-text {
  color: #111827;
  font-size: 14px;
  font-weight: 500;
}

.group-drug-shared {
  margin-top: 2px;
  color: #2563eb;
  font-size: 12px;
}

.normal-content-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.normal-drug-shared {
  border-top: 1px dashed #cbd5e1;
  padding-top: 3px;
  color: #2563eb;
  font-size: 12px;
}

/* #fallback 内容渲染在父级作用域，用 :deep 保证与原先 AdviceDrugContentCell 视觉一致 */
.normal-content-cell--fallback :deep(.drug-main-text) {
  color: #111827;
  font-size: 14px;
  font-weight: 500;
}

.normal-content-cell--fallback :deep(.group-drug-dose) {
  color: #475569;
  font-size: 12px;
}

.normal-content-cell--fallback :deep(.normal-drug-shared) {
  border-top: 1px dashed #cbd5e1;
  padding-top: 3px;
  color: #2563eb;
  font-size: 12px;
}
</style>
