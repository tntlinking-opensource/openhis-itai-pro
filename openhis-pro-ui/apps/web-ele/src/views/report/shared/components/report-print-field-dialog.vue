<!--
  报表打印设置：范围（可选）+ 字段勾选 + 横向/纵向；本机记住；不进 hiprint 设计器。
-->
<script setup lang="ts">
import type {
  ReportPrintColumnDef,
  ReportPrintOrientation,
  ReportPrintScope,
} from '#/utils/print/print-report-list';

import { computed, ref, watch } from 'vue';

import { ElButton, ElDialog, ElMessage, ElTag } from 'element-plus';

import {
  getReportPrintColumnPreference,
  normalizeReportPrintScope,
  saveReportPrintColumnPreference,
} from '#/utils/print/print-report-list';

defineOptions({ name: 'ReportPrintFieldDialog' });

interface Props {
  reportCode: string;
  columnCatalog: ReportPrintColumnDef[];
  /** 是否展示跨页全部 / 仅本页；未实现全量拉取的报表勿开 */
  showPrintScope?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  reportCode: '',
  columnCatalog: () => [],
  showPrintScope: false,
});

const emit = defineEmits<{
  saved: [
    payload: {
      fields: string[];
      orientation: ReportPrintOrientation;
      printScope: ReportPrintScope;
    },
  ];
}>();

const visible = ref(false);
const checkedFields = ref<string[]>([]);
const orientation = ref<ReportPrintOrientation>('landscape');
const printScope = ref<ReportPrintScope>('all');

/** 默认全选全部可打印列 */
const defaultFields = computed(() => props.columnCatalog.map((col) => col.field));

/** 已选字段数 / 目录总数，用于顶栏统计 */
const selectedCount = computed(() => checkedFields.value.length);
const catalogCount = computed(() => props.columnCatalog.length);

const isAllSelected = computed(
  () => catalogCount.value > 0 && selectedCount.value === catalogCount.value,
);

/** 打开弹窗并回填已存偏好 / 默认全选 + 横向 + 跨页全部 */
function open() {
  const pref = getReportPrintColumnPreference(props.reportCode);
  const allowed = new Set(props.columnCatalog.map((col) => col.field));
  const fromPref = (pref?.fields ?? []).filter((field) => allowed.has(field));
  checkedFields.value = fromPref.length > 0 ? fromPref : [...defaultFields.value];
  orientation.value = pref?.orientation === 'portrait' ? 'portrait' : 'landscape';
  printScope.value = normalizeReportPrintScope(pref?.printScope);
  visible.value = true;
}

/** 切换纸张方向 */
function handleSelectOrientation(next: ReportPrintOrientation) {
  orientation.value = next;
}

/** 切换打印范围：跨页全部 / 仅本页 */
function handleSelectPrintScope(next: ReportPrintScope) {
  printScope.value = next;
}

/** 某字段是否已勾选 */
function isFieldChecked(field: string): boolean {
  return checkedFields.value.includes(field);
}

/** 切换单个打印字段 */
function handleToggleField(field: string) {
  if (checkedFields.value.includes(field)) {
    checkedFields.value = checkedFields.value.filter((item) => item !== field);
    return;
  }
  checkedFields.value = [...checkedFields.value, field];
}

/** 全选当前目录字段 */
function handleSelectAll() {
  checkedFields.value = props.columnCatalog.map((col) => col.field);
}

/** 清空全部勾选（仍须至少选一列才能确定） */
function handleClearAll() {
  checkedFields.value = [];
}

/** 恢复默认：全选 + 横向 + 跨页全部 */
function handleResetDefault() {
  checkedFields.value = [...defaultFields.value];
  orientation.value = 'landscape';
  printScope.value = 'all';
}

/** 保存勾选、方向与打印范围并关闭 */
function handleConfirm() {
  if (checkedFields.value.length === 0) {
    ElMessage.warning('请至少选择一个打印字段');
    return;
  }
  // 按目录顺序输出，保证打印列顺序稳定
  const ordered = props.columnCatalog
    .map((col) => col.field)
    .filter((field) => checkedFields.value.includes(field));
  saveReportPrintColumnPreference(props.reportCode, {
    fields: ordered,
    orientation: orientation.value,
    ...(props.showPrintScope ? { printScope: printScope.value } : {}),
  });
  emit('saved', {
    fields: ordered,
    orientation: orientation.value,
    printScope: printScope.value,
  });
  ElMessage.success('已记住打印设置');
  visible.value = false;
}

watch(
  () => props.reportCode,
  () => {
    if (visible.value) {
      open();
    }
  },
);

defineExpose({ open });
</script>

<template>
  <ElDialog
    v-model="visible"
    title="打印设置"
    width="640px"
    destroy-on-close
    append-to-body
    class="report-print-field-dialog"
  >
    <div class="report-print-field-dialog__body">
      <div class="report-print-field-dialog__hero">
        <div class="report-print-field-dialog__hero-text">
          <div class="report-print-field-dialog__hero-title">
            {{ showPrintScope ? '打印范围、方向与字段' : '打印方向与字段' }}
          </div>
          <div class="report-print-field-dialog__hero-desc">
            纸张固定 A4；
            {{ showPrintScope ? '可跨页打全部或只打本页；' : '' }}
            横向/纵向指内容打印方向，确认后本机记住
          </div>
        </div>
        <ElTag type="primary" effect="plain" round>
          已选 {{ selectedCount }} / {{ catalogCount }}
        </ElTag>
      </div>

      <section v-if="showPrintScope" class="report-print-field-dialog__panel py-2.5">
        <div
          class="report-print-field-dialog__panel-head report-print-field-dialog__panel-head--row"
        >
          <span class="report-print-field-dialog__panel-title">打印范围</span>
          <div class="flex flex-nowrap items-center gap-2">
            <button
              type="button"
              class="report-print-field-dialog__scope-chip h-8 shrink-0 px-3"
              :class="{
                'report-print-field-dialog__scope-chip--active': printScope === 'all',
              }"
              @click="handleSelectPrintScope('all')"
            >
              跨页打印全部
            </button>
            <button
              type="button"
              class="report-print-field-dialog__scope-chip h-8 shrink-0 px-3"
              :class="{
                'report-print-field-dialog__scope-chip--active': printScope === 'currentPage',
              }"
              @click="handleSelectPrintScope('currentPage')"
            >
              仅打印本页
            </button>
          </div>
        </div>
      </section>

      <section class="report-print-field-dialog__panel">
        <div class="report-print-field-dialog__panel-head">
          <span class="report-print-field-dialog__panel-title">打印方向</span>
          <span class="report-print-field-dialog__panel-hint">纸张：A4</span>
        </div>
        <div class="report-print-field-dialog__orient">
          <button
            type="button"
            class="report-print-field-dialog__orient-card"
            :class="{
              'report-print-field-dialog__orient-card--active': orientation === 'landscape',
            }"
            @click="handleSelectOrientation('landscape')"
          >
            <span
              class="report-print-field-dialog__paper report-print-field-dialog__paper--landscape"
              aria-hidden="true"
            />
            <span class="report-print-field-dialog__orient-meta">
              <span class="report-print-field-dialog__orient-name">横向打印</span>
              <span class="report-print-field-dialog__orient-size">
                A4 · 内容沿长边（适合多列）
              </span>
            </span>
          </button>
          <button
            type="button"
            class="report-print-field-dialog__orient-card"
            :class="{
              'report-print-field-dialog__orient-card--active': orientation === 'portrait',
            }"
            @click="handleSelectOrientation('portrait')"
          >
            <span
              class="report-print-field-dialog__paper report-print-field-dialog__paper--portrait"
              aria-hidden="true"
            />
            <span class="report-print-field-dialog__orient-meta">
              <span class="report-print-field-dialog__orient-name">纵向打印</span>
              <span class="report-print-field-dialog__orient-size">
                A4 · 内容沿短边（适合少列）
              </span>
            </span>
          </button>
        </div>
      </section>

      <section class="report-print-field-dialog__panel">
        <div class="report-print-field-dialog__panel-head">
          <span class="report-print-field-dialog__panel-title">打印字段</span>
          <div class="report-print-field-dialog__panel-actions">
            <ElButton link type="primary" :disabled="isAllSelected" @click="handleSelectAll">
              全选
            </ElButton>
            <ElButton link type="primary" :disabled="selectedCount === 0" @click="handleClearAll">
              清空
            </ElButton>
            <ElButton link type="primary" @click="handleResetDefault">恢复默认</ElButton>
          </div>
        </div>
        <div class="report-print-field-dialog__fields">
          <button
            v-for="col in columnCatalog"
            :key="col.field"
            type="button"
            class="report-print-field-dialog__field"
            :class="{
              'report-print-field-dialog__field--active': isFieldChecked(col.field),
            }"
            @click="handleToggleField(col.field)"
          >
            <span
              class="report-print-field-dialog__check"
              :class="{
                'report-print-field-dialog__check--on': isFieldChecked(col.field),
              }"
              aria-hidden="true"
            />
            <span class="report-print-field-dialog__field-title">{{ col.title }}</span>
          </button>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="report-print-field-dialog__footer">
        <span class="report-print-field-dialog__footer-hint">
          A4 {{ orientation === 'landscape' ? '横向打印' : '纵向打印' }}
          <template v-if="showPrintScope">
            · {{ printScope === 'all' ? '跨页全部' : '仅本页' }}
          </template>
          · {{ selectedCount }} 列
        </span>
        <div class="report-print-field-dialog__footer-actions">
          <ElButton @click="visible = false">取消</ElButton>
          <ElButton type="primary" @click="handleConfirm">确定并记住</ElButton>
        </div>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.report-print-field-dialog__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.report-print-field-dialog__hero {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.report-print-field-dialog__hero-title {
  margin-bottom: 2px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.report-print-field-dialog__hero-desc {
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-regular);
}

.report-print-field-dialog__panel {
  padding: 12px 14px 14px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.report-print-field-dialog__panel-head {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.report-print-field-dialog__panel-head--row {
  margin-bottom: 0;
}

.report-print-field-dialog__panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.report-print-field-dialog__panel-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.report-print-field-dialog__panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  justify-content: flex-end;
}

.report-print-field-dialog__scope-chip {
  font-size: 13px;
  line-height: 1;
  color: var(--el-text-color-primary);
  cursor: pointer;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.report-print-field-dialog__scope-chip:hover {
  border-color: var(--el-color-primary-light-5);
}

.report-print-field-dialog__scope-chip--active {
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.report-print-field-dialog__orient {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.report-print-field-dialog__orient-card {
  display: flex;
  gap: 12px;
  align-items: center;
  min-height: 72px;
  padding: 12px 14px;
  cursor: pointer;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease,
    box-shadow 0.15s ease;
}

.report-print-field-dialog__orient-card:hover {
  border-color: var(--el-color-primary-light-5);
}

.report-print-field-dialog__orient-card--active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  box-shadow: inset 0 0 0 1px var(--el-color-primary-light-7);
}

.report-print-field-dialog__paper {
  flex-shrink: 0;
  background: var(--el-bg-color);
  border: 1.5px solid var(--el-border-color-darker);
  border-radius: 2px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 6%);
}

.report-print-field-dialog__orient-card--active .report-print-field-dialog__paper {
  border-color: var(--el-color-primary);
}

.report-print-field-dialog__paper--landscape {
  width: 34px;
  height: 24px;
}

.report-print-field-dialog__paper--portrait {
  width: 24px;
  height: 34px;
}

.report-print-field-dialog__orient-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
  text-align: left;
}

.report-print-field-dialog__orient-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.report-print-field-dialog__orient-size {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.report-print-field-dialog__fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  max-height: 280px;
  padding-right: 2px;
  overflow: auto;
}

.report-print-field-dialog__field {
  display: flex;
  gap: 8px;
  align-items: center;
  min-height: 40px;
  padding: 8px 10px;
  cursor: pointer;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease;
}

.report-print-field-dialog__field:hover {
  border-color: var(--el-color-primary-light-5);
}

.report-print-field-dialog__field--active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.report-print-field-dialog__check {
  position: relative;
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  background: var(--el-bg-color);
  border: 1.5px solid var(--el-border-color);
  border-radius: 4px;
}

.report-print-field-dialog__check--on {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.report-print-field-dialog__check--on::after {
  position: absolute;
  top: 1px;
  left: 4px;
  width: 4px;
  height: 8px;
  content: '';
  border: solid var(--el-color-white);
  border-width: 0 1.5px 1.5px 0;
  transform: rotate(45deg);
}

.report-print-field-dialog__field-title {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  line-height: 1.3;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.report-print-field-dialog__footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.report-print-field-dialog__footer-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.report-print-field-dialog__footer-actions {
  display: flex;
  gap: 8px;
}
</style>
