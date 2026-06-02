<script setup lang="ts">
import type { PatientStatusFilter } from './patient-list-panel.context';

import { inject, toRef } from 'vue';

import { DArrowLeft, DArrowRight, Refresh } from '@element-plus/icons-vue';

import { encounterStatusToNurseStationLabel } from '../services/map-inpatient-nurse-station-api';
import {
  formatNurseStationAccountBalance,
  resolveNurseStationPatientBalanceTone,
  resolveNurseStationPatientStatusTone,
} from './nurse-station-module-patient.types';
import { nurseStationPatientListPanelContextKey } from './patient-list-panel.context';

defineOptions({
  name: 'InpatientNurseStationPatientListPanel',
});

const injectedContext = inject(nurseStationPatientListPanelContextKey);
if (!injectedContext) {
  throw new Error('PatientListPanel context not provided');
}
const context = injectedContext;
const {
  availableWards,
  changeKeyword,
  changeRegionIds,
  changeStatusFilter,
  changeWardIds,
  filterPanelExpanded,
  filteredPatients,
  keyword,
  onPatientDragEnd,
  onPatientDragStart,
  pendingAuditCount,
  pendingExecuteCount,
  regionNameByIdRecord,
  selectedRegionIds,
  selectedWardIds,
  statusFilter,
  statusOptions,
  toggleFilterPanel,
  togglePatientCardSelection,
  onPatientCardDblClick,
  togglePatientSelection,
  totalPatientCount,
  wardNameByIdRecord,
} = context;
/** 病区选项来自接口，保持 Ref 以在树加载后更新勾选列表。 */
const regionOptions = toRef(context, 'regionOptions');

/** 姓名/病区/床号搜索词变更后拉取队列。 */
function handleKeywordInput(value: string) {
  changeKeyword(value);
}

/** 切换患者状态筛选并拉取队列。 */
function handleStatusFilterChange(value: PatientStatusFilter) {
  changeStatusFilter(value);
}

/** 病区勾选变化后拉取队列。 */
function handleRegionIdsChange(value: string[]) {
  changeRegionIds(value);
}

/** 病房勾选变化后拉取队列。 */
function handleWardIdsChange(value: string[]) {
  changeWardIds(value);
}

/** 切换病区/病房筛选区显隐。 */
function handleToggleFilterPanel() {
  toggleFilterPanel();
}

/** 重置筛选并拉取队列。 */
function handleRefreshFilters() {
  void context.refreshPatientFilters();
}

/** 卡片点击切换患者选中。 */
function handleCardClick(id: string) {
  togglePatientCardSelection(id);
}

/** 双击卡片打开患者入科选床弹窗。 */
function handleCardDblClick(patient: (typeof filteredPatients.value)[number]) {
  onPatientCardDblClick(patient);
}

/** 勾选框选择变化后同步到父层。 */
function handleCheckboxChange(id: string, value: boolean) {
  togglePatientSelection(id, value);
}

/** 开始拖拽患者卡片并上抛给父层组装载荷。 */
function handlePatientDragStart(
  event: DragEvent,
  patient: (typeof filteredPatients.value)[number],
) {
  onPatientDragStart(event, patient);
}

/** 结束拖拽后通知父层清理拖拽预览。 */
function handlePatientDragEnd() {
  onPatientDragEnd();
}

/** 是否尚未分床（无床位展示名） */
function isBedUnassigned(bedNo: string | undefined): boolean {
  return !bedNo?.trim();
}

/** 护理级别联调占位（TODO: 读模型/字典对接后改为 row.level） */
const nurseLevelPlaceholder = '二级护理';

/** 患者卡片就诊状态展示文案 */
function formatPatientStatusLabel(row: (typeof filteredPatients.value)[number]): string {
  return encounterStatusToNurseStationLabel(row.statusEnum, row.statusEnumText);
}

/** 患者卡片预交金余额展示文案 */
function formatPatientBalanceLabel(row: (typeof filteredPatients.value)[number]): string {
  return formatNurseStationAccountBalance(row.accountBalance);
}

/** 患者卡片就诊状态色调 */
function resolvePatientStatusTone(row: (typeof filteredPatients.value)[number]): string {
  return resolveNurseStationPatientStatusTone(row.statusEnum);
}

/** 患者卡片余额色调 */
function resolvePatientBalanceTone(row: (typeof filteredPatients.value)[number]): string {
  return resolveNurseStationPatientBalanceTone(row.accountBalance, row.lowBalanceWarning);
}
</script>

<template>
  <div class="nurse-workbench__patient">
    <div
      class="nurse-top-bar rounded-sm border-b border-l border-r border-[var(--el-border-color-light)] bg-white"
    >
      <div class="nurse-top-overview">
        <div class="nurse-top-stats">
          <div class="nurse-top-stat nurse-top-stat--total">
            <div class="nurse-top-stat__label">总人数</div>
            <div class="nurse-top-stat__value">{{ totalPatientCount }}</div>
          </div>
          <div class="nurse-top-stat nurse-top-stat--audit">
            <div class="nurse-top-stat__label">待校对</div>
            <div class="nurse-top-stat__value">{{ pendingAuditCount }}</div>
          </div>
          <div class="nurse-top-stat nurse-top-stat--execute">
            <div class="nurse-top-stat__label">待执行</div>
            <div class="nurse-top-stat__value">{{ pendingExecuteCount }}</div>
          </div>
        </div>
        <div class="nurse-top-overview__right"></div>
      </div>
    </div>

    <div class="nurse-content">
      <div class="nurse-search-bar">
        <ElInput
          :model-value="keyword"
          clearable
          placeholder="姓名/病区/床号模糊搜索"
          class="nurse-search-bar__keyword"
          @update:model-value="handleKeywordInput"
        />
        <ElSelect
          :model-value="statusFilter"
          class="nurse-search-bar__status"
          @update:model-value="handleStatusFilterChange"
        >
          <ElOption
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
        <ElButton :icon="Refresh" circle title="刷新" @click.stop="handleRefreshFilters" />
      </div>
      <div class="nurse-content-body">
        <div class="nurse-filter-panel" :class="{ 'is-collapsed': !filterPanelExpanded }">
          <div class="nurse-filter-panel__toolbar">
            <ElButton
              link
              type="primary"
              :title="filterPanelExpanded ? '收起病区/病房' : '展开病区/病房'"
              @click.stop="handleToggleFilterPanel"
            >
              <ElIcon>
                <component :is="filterPanelExpanded ? DArrowLeft : DArrowRight" />
              </ElIcon>
            </ElButton>
          </div>
          <div v-show="filterPanelExpanded" class="nurse-filter-panel__body">
            <div class="nurse-filter-block">
              <div class="nurse-filter-block__title">病区</div>
              <ElCheckboxGroup
                :model-value="selectedRegionIds"
                class="nurse-filter-group flex flex-col gap-2"
                @update:model-value="handleRegionIdsChange"
              >
                <ElCheckbox v-for="item in regionOptions" :key="item.id" :label="item.id">
                  {{ item.name }}
                </ElCheckbox>
              </ElCheckboxGroup>
            </div>
            <div class="nurse-filter-block">
              <div class="nurse-filter-block__title">病房</div>
              <ElCheckboxGroup
                :model-value="selectedWardIds"
                class="nurse-filter-group flex flex-col gap-2"
                @update:model-value="handleWardIdsChange"
              >
                <ElCheckbox v-for="item in availableWards" :key="item.id" :label="item.id">
                  {{ item.name }}
                </ElCheckbox>
              </ElCheckboxGroup>
              <ElEmpty
                v-if="availableWards.length === 0"
                :image-size="54"
                description="请先选择病区"
              />
            </div>
          </div>
        </div>
        <div class="nurse-content-divider"></div>
        <div class="nurse-main">
          <div class="nurse-patient-panel">
            <div class="nurse-table">
              <template v-if="filteredPatients.length === 0">
                <div class="flex min-h-80 items-center justify-center">
                  <ElEmpty description="暂无符合条件的患者" />
                </div>
              </template>
              <template v-else>
                <div class="nurse-card-list">
                  <div
                    v-for="row in filteredPatients"
                    :key="row.id"
                    class="nurse-patient-card nurse-patient-card--draggable"
                    :class="{ 'is-selected': row.selected }"
                    title="按住拖动到右侧「入出转管理」床位图，在目标床位上松开以分床或换床"
                    draggable="true"
                    @dragstart="handlePatientDragStart($event, row)"
                    @dragend="handlePatientDragEnd"
                    @click="handleCardClick(row.id)"
                    @dblclick.stop="handleCardDblClick(row)"
                  >
                    <div class="nurse-patient-card__line1">
                      <ElCheckbox
                        v-model="row.selected"
                        @click.stop
                        @mousedown.stop
                        @change="(value: boolean) => handleCheckboxChange(row.id, value)"
                      />
                      <div class="nurse-patient-card__name-block">
                        <div class="nurse-patient-card__name-row">
                          <span class="nurse-patient-card__name">{{ row.name }}</span>
                          <span
                            class="nurse-patient-card__level-wrap"
                            :title="nurseLevelPlaceholder"
                          >
                            <span class="nurse-patient-card__level-dot">·</span>
                            <span class="nurse-patient-card__level truncate">{{
                              nurseLevelPlaceholder
                            }}</span>
                          </span>
                          <span class="nurse-patient-card__meta"
                            >{{ row.gender }} / {{ row.age }}岁 · {{ row.feeType }}</span
                          >
                        </div>
                        <div class="nurse-patient-card__sub-row">
                          <span
                            class="nurse-patient-card__status"
                            :class="`is-${resolvePatientStatusTone(row)}`"
                          >
                            {{ formatPatientStatusLabel(row) }}
                          </span>
                          <span class="nurse-patient-card__balance-wrap">
                            <span class="nurse-patient-card__balance-label">余额</span>
                            <span
                              class="nurse-patient-card__balance-value"
                              :class="`is-${resolvePatientBalanceTone(row)}`"
                            >
                              {{ formatPatientBalanceLabel(row) }}
                            </span>
                          </span>
                        </div>
                      </div>
                    </div>
                    <div class="nurse-patient-card__diag">
                      <span class="nurse-patient-card__diag-label">诊断：</span>
                      <span class="nurse-patient-card__diag-text truncate" :title="row.diagnosis">
                        {{ row.diagnosis }}
                      </span>
                    </div>
                    <div class="nurse-patient-card__line2">
                      <div class="nurse-patient-card__location truncate">
                        <span>{{ regionNameByIdRecord[row.regionId] || '-' }}</span>
                        <span class="nurse-patient-card__location-sep">|</span>
                        <span>{{ wardNameByIdRecord[row.wardId] || '-' }}</span>
                        <span class="nurse-patient-card__location-sep">|</span>
                        <span
                          class="nurse-patient-card__bed"
                          :class="{ 'is-unassigned': isBedUnassigned(row.bedNo) }"
                        >
                          {{ row.bedNo?.trim() || '未分床' }}
                        </span>
                      </div>
                      <div class="nurse-patient-card__tasks">
                        <span class="nurse-patient-card__audit">校 {{ row.pendingAudit }}</span>
                        <span class="nurse-patient-card__execute">执 {{ row.pendingExecute }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nurse-workbench__patient {
  background: #fff;
  border-right: 1px solid var(--el-border-color);
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  width: 22%;
}
.nurse-top-overview {
  align-items: center;
  display: flex;
  height: 100%;
  justify-content: space-between;
  width: 100%;
}
.nurse-top-bar {
  align-items: center;
  box-sizing: border-box;
  display: flex;
  height: 47px;
  padding: 0 8px;
}
.nurse-top-overview__right {
  min-width: 12px;
}
.nurse-top-stats {
  display: flex;
  gap: 0;
  width: 100%;
}
.nurse-top-stat {
  align-items: center;
  display: flex;
  flex: 1 1 0;
  justify-content: space-between;
  min-width: 0;
  padding: 0 12px;
}
.nurse-top-stat + .nurse-top-stat {
  border-left: 1px solid var(--el-border-color-lighter);
}
.nurse-top-stat__label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1;
}
.nurse-top-stat__value {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1;
}
.nurse-top-stat--total .nurse-top-stat__value {
  color: var(--el-color-primary);
}
.nurse-top-stat--audit .nurse-top-stat__value {
  color: #e6a23c;
}
.nurse-top-stat--execute .nurse-top-stat__value {
  color: #f56c6c;
}
.nurse-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
.nurse-content-body {
  display: flex;
  flex: 1;
  gap: 8px;
  min-height: 0;
}
.nurse-filter-panel {
  display: flex;
  flex: 0 0 104px;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding: 6px 0 6px 6px;
}
.nurse-filter-panel.is-collapsed {
  flex: 0 0 36px;
  overflow: visible;
  padding: 6px 2px;
}
.nurse-filter-panel__toolbar {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
  margin-bottom: 6px;
}
.nurse-filter-panel.is-collapsed .nurse-filter-panel__toolbar {
  justify-content: center;
  margin-bottom: 0;
}
.nurse-filter-panel__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.nurse-filter-block {
  margin-bottom: 10px;
}
.nurse-filter-group {
  padding-left: 4px;
}
.nurse-filter-block__title {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.nurse-content-divider {
  background: var(--el-border-color-lighter);
  margin: 8px 0;
  width: 1px;
}
.nurse-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}
.nurse-patient-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}
.nurse-search-bar {
  align-items: center;
  display: flex;
  gap: 8px;
  padding: 6px 8px;
}
.nurse-search-bar__keyword {
  flex: 1;
  min-width: 0;
}
.nurse-search-bar__status {
  width: 132px;
}
.nurse-table {
  flex: 1;
  min-height: 0;
}
.nurse-card-list {
  height: 100%;
  overflow: auto;
  padding: 6px;
}
.nurse-patient-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  margin-bottom: 6px;
  min-height: 118px;
  padding: 6px 10px 5px;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}
.nurse-patient-card__line1,
.nurse-patient-card__line2 {
  align-items: center;
  display: flex;
  gap: 8px;
}
.nurse-patient-card__line1 {
  align-items: flex-start;
  border-bottom: 1px dashed #cdd5e1;
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
  min-width: 0;
  padding: 4px 0 8px;
}
.nurse-patient-card__name-block {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.nurse-patient-card__name-row {
  align-items: center;
  display: flex;
  gap: 8px;
  min-width: 0;
}
.nurse-patient-card__sub-row {
  align-items: center;
  display: flex;
  font-size: 12px;
  justify-content: space-between;
  line-height: 1.35;
  min-width: 0;
}
.nurse-patient-card__status {
  flex-shrink: 0;
  font-weight: 600;
}
.nurse-patient-card__status.is-registered {
  color: var(--el-color-info);
}
.nurse-patient-card__status.is-in-progress {
  color: var(--el-color-success);
}
.nurse-patient-card__status.is-on-hold,
.nurse-patient-card__status.is-pending {
  color: var(--el-color-warning);
}
.nurse-patient-card__status.is-completed {
  color: var(--el-color-primary);
}
.nurse-patient-card__status.is-discharged,
.nurse-patient-card__status.is-default {
  color: var(--el-text-color-secondary);
}
.nurse-patient-card__balance-wrap {
  align-items: baseline;
  display: flex;
  flex-shrink: 0;
  gap: 4px;
  margin-left: 8px;
}
.nurse-patient-card__balance-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}
.nurse-patient-card__balance-value {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.nurse-patient-card__balance-value.is-danger {
  color: var(--el-color-danger);
}
.nurse-patient-card__balance-value.is-warning {
  color: var(--el-color-warning);
}
.nurse-patient-card__balance-value.is-success {
  color: var(--el-color-success);
}
.nurse-patient-card__balance-value.is-primary {
  color: var(--el-color-primary);
}
.nurse-patient-card__balance-value.is-normal {
  color: var(--el-text-color-regular);
}
.nurse-patient-card__name {
  color: var(--el-text-color-primary);
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.2;
}
.nurse-patient-card__level-wrap {
  align-items: center;
  display: flex;
  flex: 1;
  font-size: 12px;
  line-height: 1.4;
  min-width: 0;
}
.nurse-patient-card__level-dot {
  color: #9ca3af;
  flex-shrink: 0;
}
.nurse-patient-card__level {
  color: #2563eb;
  flex: 1;
  font-weight: 500;
  min-width: 0;
}
.nurse-patient-card__meta {
  color: #6b7280;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 400;
  margin-left: auto;
}
.nurse-patient-card__diag {
  align-items: center;
  color: #6b7280;
  display: flex;
  font-size: 12px;
  line-height: 1.35;
  margin: 2px 0;
  min-width: 0;
}
.nurse-patient-card__diag-label {
  flex-shrink: 0;
}
.nurse-patient-card__diag-text {
  flex: 1;
  min-width: 0;
}
.nurse-patient-card__line2 {
  color: #6b7280;
  font-size: 12px;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 2px;
}
.nurse-patient-card__location {
  align-items: center;
  display: flex;
  flex: 1;
  gap: 4px;
}
.nurse-patient-card__location-sep {
  color: #9ca3af;
}
.nurse-patient-card__bed.is-unassigned {
  color: #e6a23c;
  font-weight: 600;
}
.nurse-patient-card__tasks {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-left: auto;
  text-align: right;
}
.nurse-patient-card__audit {
  color: #e6a23c;
}
.nurse-patient-card__execute {
  color: #f56c6c;
}
.nurse-patient-card:hover {
  background: rgb(249 250 251 / 50%);
  border-color: rgb(59 130 246 / 30%);
}
.nurse-patient-card.is-selected {
  background: rgb(59 130 246 / 5%);
  border-color: var(--el-color-primary);
  box-shadow: 0 1px 2px rgb(15 23 42 / 8%);
}

.nurse-patient-card--draggable {
  cursor: grab;
}

.nurse-patient-card--draggable:active {
  cursor: grabbing;
}
</style>
