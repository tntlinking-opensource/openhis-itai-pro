<script setup lang="ts">
import type { AdviceRowItem } from '../advice/services/advice.types';
import type { ServiceApplyDocumentGroup } from './service-apply-doc-groups';

import type { ServiceApplyKind } from './service-apply.types';

import { computed } from 'vue';

import { ElButton, ElCheckbox, ElTag } from 'element-plus';

import {
  appendRehabTreatmentCourseRemark,
  buildServiceApplyFormDetailHint,
} from '#/utils/service-apply-display';

import { rowLooksDraftForSign } from '../advice/services/advice-row';
import {
  inferServiceApplyKindFromAdviceRow,
  sumServiceApplyDocumentTotalAmount,
} from './service-apply-doc-groups';
import { SERVICE_APPLY_KIND_LABEL } from './service-apply.constants';

defineOptions({ name: 'ServiceApplyFormDocument' });

interface Props {
  group: ServiceApplyDocumentGroup;
  selected?: boolean;
  getStatusTagType: (row: AdviceRowItem) => 'danger' | 'info' | 'primary' | 'success' | 'warning';
  getChargeStatusTagType: (
    row: AdviceRowItem,
  ) => 'danger' | 'info' | 'primary' | 'success' | 'warning';
}

const props = withDefaults(defineProps<Props>(), {
  selected: false,
});

const emit = defineEmits<{
  edit: [];
  'toggle-select': [];
}>();

/** 申请单首行（页眉/页脚共用） */
const headLine = computed(() => props.group.lines[0]);

/** 检验/检查类型 */
const applyKind = computed((): ServiceApplyKind | null => {
  for (const line of props.group.lines) {
    const kind = inferServiceApplyKindFromAdviceRow(line);
    if (kind) {
      return kind;
    }
  }
  return inferServiceApplyKindFromAdviceRow(headLine.value);
});

/** 类型短标签 */
const kindShortLabel = computed(() => {
  if (applyKind.value) {
    return SERVICE_APPLY_KIND_LABEL[applyKind.value];
  }
  return trimText(headLine.value?.type) || '申请';
});

/** 单据标题 */
const documentTitle = computed(() => {
  if (applyKind.value) {
    return `${SERVICE_APPLY_KIND_LABEL[applyKind.value]}申请单`;
  }
  return trimText(headLine.value?.type) || '申请单';
});

/** 明细表「样本/部位」列名 */
const detailColumnLabel = computed(() => (applyKind.value === 'lab' ? '样本' : '部位'));

/** 合计金额 */
const totalAmountText = computed(() => sumServiceApplyDocumentTotalAmount(props.group.lines));

/** 注意事项：合并各行 remark（去重）；康复科治疗单追加固定疗程说明 */
const precautionText = computed(() => {
  const parts: string[] = [];
  // 合并本单各行备注，去重后作为基础注意事项
  for (const line of props.group.lines) {
    const remark = trimText(line.remark);
    if (remark && !parts.includes(remark)) {
      parts.push(remark);
    }
  }
  return appendRehabTreatmentCourseRemark(
    parts.join('；'),
    applyKind.value,
    headLine.value?.performOrgName,
    '；',
  );
});

/** 去掉「样本：/部位：」前缀，仅保留明细值 */
function resolveLineDetailText(line: AdviceRowItem): string {
  const hint = buildServiceApplyFormDetailHint(line.serviceRequestDetails);
  const splitIndex = hint.indexOf('：');
  if (splitIndex >= 0) {
    return hint.slice(splitIndex + 1);
  }
  return hint;
}

/** 开立数量展示 */
function resolveQuantityText(line: AdviceRowItem): string {
  const quantity = line.listQuantity;
  if (typeof quantity === 'number' && Number.isFinite(quantity) && quantity > 0) {
    return String(quantity);
  }
  return '1';
}

/** 执行次数展示 */
function resolveExecutionCountText(line: AdviceRowItem): string {
  const count = line.listExecutionCount;
  if (typeof count === 'number' && Number.isFinite(count) && count > 0) {
    return `${Math.trunc(count)}次`;
  }
  return '1次';
}

function trimText(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 草稿态申请单允许修改 */
const canEdit = computed(() => {
  const head = headLine.value;
  return head ? rowLooksDraftForSign(head) : false;
});

/** 弹窗修改申请单 */
function handleEdit() {
  emit('edit');
}

/** 切换整张申请单选中 */
function handleToggleSelect() {
  emit('toggle-select');
}

/** 点击卡片空白区域切换选中 */
function handleCardClick() {
  handleToggleSelect();
}
</script>

<template>
  <article
    class="apply-sheet"
    :class="[
      selected ? 'is-selected' : '',
      applyKind === 'lab' ? 'is-lab' : '',
      applyKind === 'exam' ? 'is-exam' : '',
      applyKind === 'treatment' ? 'is-treatment' : '',
    ]"
    @click="handleCardClick"
  >
    <div class="apply-sheet__inner">
      <div class="apply-sheet__top">
        <div class="apply-sheet__select">
          <ElCheckbox :model-value="selected" @click.stop @change="handleToggleSelect" />
        </div>
        <div class="apply-sheet__identity">
          <span class="apply-sheet__kind">{{ kindShortLabel }}</span>
          <span class="apply-sheet__no">{{ group.applyFormNo }}</span>
        </div>
        <ElTag
          v-if="headLine"
          :type="getStatusTagType(headLine)"
          size="small"
          effect="plain"
          round
          class="apply-sheet__status"
        >
          {{ headLine.status }}
        </ElTag>
        <ElButton
          v-if="canEdit"
          type="primary"
          link
          size="small"
          class="apply-sheet__edit"
          @click.stop="handleEdit"
        >
          修改
        </ElButton>
      </div>

      <div class="apply-sheet__headline">
        <h3 class="apply-sheet__title">{{ documentTitle }}</h3>
        <div class="apply-sheet__rule" />
      </div>

      <dl class="apply-sheet__meta">
        <div v-if="trimText(headLine?.diagnosisSummary)" class="meta-row meta-row--full">
          <dt>临床诊断</dt>
          <dd>{{ headLine?.diagnosisSummary }}</dd>
        </div>
        <div class="meta-row">
          <dt>开立医师</dt>
          <dd>{{ headLine?.orderingDoctor || '—' }}</dd>
        </div>
        <div class="meta-row">
          <dt>开立时间</dt>
          <dd>{{ headLine?.startTime || '—' }}</dd>
        </div>
        <div v-if="precautionText" class="meta-row meta-row--full">
          <dt>注意事项</dt>
          <dd>{{ precautionText }}</dd>
        </div>
      </dl>

      <div class="apply-sheet__items">
        <div class="items-header">
          <span class="col-index">序号</span>
          <span class="col-name">项目名称</span>
          <span class="col-detail">{{ detailColumnLabel }}</span>
          <span class="col-qty">数量</span>
          <span class="col-count">次数</span>
          <span class="col-charge">费用状态</span>
          <span class="col-amount">金额</span>
        </div>
        <div v-for="(line, index) in group.lines" :key="line.key" class="items-row">
          <span class="col-index">{{ index + 1 }}</span>
          <span class="col-name">{{ line.content }}</span>
          <span class="col-detail">{{ resolveLineDetailText(line) || '—' }}</span>
          <span class="col-qty">{{ resolveQuantityText(line) }}</span>
          <span class="col-count">{{ resolveExecutionCountText(line) }}</span>
          <span class="col-charge">
            <ElTag :type="getChargeStatusTagType(line)" size="small" effect="light">
              {{ line.chargeStatus || '—' }}
            </ElTag>
          </span>
          <span class="col-amount">{{ line.lineTotalAmount }}</span>
        </div>
      </div>

      <div class="apply-sheet__total">
        <span class="total-label">合计金额</span>
        <strong class="total-value">{{ totalAmountText }}</strong>
      </div>
    </div>
  </article>
</template>

<style scoped>
.apply-sheet {
  min-height: 320px;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  box-shadow:
    0 1px 2px rgb(15 23 42 / 4%),
    0 8px 24px rgb(15 23 42 / 4%);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.apply-sheet:hover {
  border-color: var(--el-border-color);
  box-shadow:
    0 2px 4px rgb(15 23 42 / 6%),
    0 12px 28px rgb(15 23 42 / 6%);
}

.apply-sheet.is-selected {
  border-color: var(--el-color-primary-light-5);
  box-shadow:
    0 0 0 1px var(--el-color-primary-light-7),
    0 12px 28px rgb(15 23 42 / 8%);
}

.apply-sheet__inner {
  display: flex;
  flex-direction: column;
  min-height: 320px;
  padding: 14px 18px 16px;
}

.apply-sheet__top {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.apply-sheet__select {
  flex-shrink: 0;
  margin-left: -2px;
}

.apply-sheet__identity {
  display: flex;
  flex: 1;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.apply-sheet__kind {
  flex-shrink: 0;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 999px;
}

.is-exam .apply-sheet__kind {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.is-treatment .apply-sheet__kind {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.apply-sheet__no {
  overflow: hidden;
  font-family: ui-monospace, 'Cascadia Code', 'Segoe UI Mono', monospace;
  font-size: 12px;
  color: var(--el-text-color-regular);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.apply-sheet__status {
  flex-shrink: 0;
}

.apply-sheet__edit {
  flex-shrink: 0;
  margin-left: auto;
}

.apply-sheet__headline {
  margin-bottom: 12px;
  text-align: center;
}

.apply-sheet__title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--el-text-color-primary);
  letter-spacing: 0.12em;
}

.apply-sheet__rule {
  width: 120px;
  height: 3px;
  margin: 0 auto;
  background: linear-gradient(
    to right,
    transparent,
    var(--el-border-color) 20%,
    var(--el-border-color) 80%,
    transparent
  );
  border-radius: 999px;
}

.apply-sheet__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 20px;
  padding: 12px 14px;
  margin: 0 0 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 6px;
}

.meta-row {
  display: flex;
  gap: 8px;
  align-items: baseline;
  min-width: 0;
  font-size: 13px;
}

.meta-row--full {
  grid-column: 1 / -1;
}

.meta-row dt {
  flex-shrink: 0;
  margin: 0;
  color: var(--el-text-color-regular);
}

.meta-row dt::after {
  content: '：';
}

.meta-row dd {
  min-width: 0;
  margin: 0;
  color: var(--el-text-color-primary);
  word-break: break-word;
  white-space: pre-wrap;
}

.apply-sheet__items {
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.items-header,
.items-row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) minmax(64px, 0.6fr) 48px 48px 88px 80px;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
}

.items-header {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.items-row {
  color: var(--el-text-color-primary);
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.items-row:last-child {
  border-bottom: none;
}

.col-index,
.col-qty,
.col-count,
.col-charge {
  text-align: center;
}

.col-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-detail {
  overflow: hidden;
  color: var(--el-text-color-regular);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-amount {
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.apply-sheet__total {
  display: flex;
  flex-shrink: 0;
  gap: 12px;
  align-items: baseline;
  justify-content: flex-end;
  padding-top: 12px;
  margin-top: auto;
}

.total-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.total-value {
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}
</style>
