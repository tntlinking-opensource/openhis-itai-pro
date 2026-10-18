<script setup lang="ts">
import type {
  ArchiveApprovalBizCategory,
  PendingArchiveDetailDto,
  PendingArchiveListItemDto,
} from '#/api/mrms/types';

import { computed, nextTick, onActivated, onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';
import { formatDateTime } from '@zxplat/utils';

import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
} from 'element-plus';

import { getDocumentVersionContentApi, getDocumentVersionListApi } from '#/api/emr/document';
import {
  approveArchiveApi,
  getPendingArchiveDetailApi,
  pagePendingArchiveApi,
  rejectArchiveApi,
} from '#/api/mrms';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  ARCHIVE_APPROVAL_BIZ_CATEGORY_OPTIONS,
  CASE_RECORD_STATUS_LABEL,
} from '../mrms.constants';
import { extractEmrPlainText } from './emr-plain-text';
import { extractHomepageCompareText } from './homepage-fields';
import { buildTextDiffRows, type DiffRow } from './text-diff';

defineOptions({ name: 'MrmsArchiveApproval' });

/**
 * 归档审批工作台（T1）：左队列 + 统一滚动左右对比。
 */

/** 左侧队列审核态 */
type ArchiveQueueReviewStatus = 'pending' | 'approved' | 'rejected';

const listLoading = ref(false);
const detailLoading = ref(false);
const tableData = ref<PendingArchiveListItemDto[]>([]);
const activeBizId = ref('');

/** 左侧类型：全部 / 门诊病历 / 住院病历 / 病案首页，默认全部 */
type QueueCategoryFilter = 'all' | ArchiveApprovalBizCategory;
const activeCategory = ref<QueueCategoryFilter>('all');

const CATEGORY_FILTERS: { value: QueueCategoryFilter; label: string }[] = [
  { value: 'all', label: '全部' },
  ...ARCHIVE_APPROVAL_BIZ_CATEGORY_OPTIONS.map((opt) => ({
    value: String(opt.value) as ArchiveApprovalBizCategory,
    label: String(opt.label),
  })),
];

const ARCHIVE_QUERY_CATEGORIES: ArchiveApprovalBizCategory[] = [
  'op_document',
  'ip_document',
  'case_homepage',
];

/** 左侧状态筛：默认只看待审 */
type QueueStatusFilter = 'all' | ArchiveQueueReviewStatus;
const statusFilter = ref<QueueStatusFilter>('pending');

/** 患者分组折叠：key → 是否收起 */
const collapsedGroups = ref<Record<string, boolean>>({});

const REVIEW_STATUS_LABEL: Record<ArchiveQueueReviewStatus, string> = {
  pending: '待审',
  approved: '通过',
  rejected: '驳回',
};

const STATUS_FILTERS: { value: QueueStatusFilter; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'pending', label: '待审' },
  { value: 'approved', label: '通过' },
  { value: 'rejected', label: '驳回' },
];

const queryParams = reactive<{
  keyword?: string;
  pageNo: number;
  pageSize: number;
}>({
  pageNo: 1,
  pageSize: 50,
  keyword: undefined,
});

const detail = ref<null | PendingArchiveDetailDto>(null);
const leftPlainText = ref('');
const rightPlainText = ref('');
const leftLabel = ref('历史版本');
const rightLabel = ref('提交内容');
const hasBaseline = ref(false);

const rejectDialogVisible = ref(false);
const rejectReason = ref('');

/** 统一滚动容器：左右同行，无需双栏同步 */
const compareScrollRef = ref<HTMLElement | null>(null);

/** 类别 + 主键，避免不同业务主键撞车 */
function rowKeyOf(row?: { bizCategory?: string; bizId?: unknown } | null): string {
  if (!row) return '';
  return `${row.bizCategory ?? ''}:${preserveApiIdForRequest(row.bizId)}`;
}

const activeRow = computed(() =>
  tableData.value.find((r) => rowKeyOf(r) === activeBizId.value),
);

const diffRows = computed<DiffRow[]>(() =>
  buildTextDiffRows(leftPlainText.value, rightPlainText.value),
);

const hasSelection = computed(() => Boolean(detail.value || activeRow.value));

/** 关键字命中的队列（状态筛之前，用来算角标） */
const keywordMatchedRows = computed(() => {
  const keyword = queryParams.keyword?.trim().toLowerCase();
  if (!keyword) return tableData.value;
  return tableData.value.filter((row) => {
    const hay = [row.title, row.patientName, row.submitterName, row.encounterId]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    return hay.includes(keyword);
  });
});

/** 各审核态数量（当前关键字下） */
const statusCounts = computed(() => {
  let pending = 0;
  let approved = 0;
  let rejected = 0;
  for (const row of keywordMatchedRows.value) {
    const status = reviewStatusOf(row);
    if (status === 'approved') approved += 1;
    else if (status === 'rejected') rejected += 1;
    else pending += 1;
  }
  return {
    all: keywordMatchedRows.value.length,
    pending,
    approved,
    rejected,
    reviewed: approved + rejected,
  };
});

/** 当前筛出来的文书行 */
const visibleQueueRows = computed(() => {
  const filter = statusFilter.value;
  if (filter === 'all') return keywordMatchedRows.value;
  return keywordMatchedRows.value.filter((row) => reviewStatusOf(row) === filter);
});

type QueueCard = {
  key: string;
  row: PendingArchiveListItemDto;
  mark: string;
  markClass: string;
  title: string;
  patientName: string;
  sub: string;
  reviewStatus: ArchiveQueueReviewStatus;
  statusLabel: string;
};

type QueueGroupView = {
  key: string;
  name: string;
  cards: QueueCard[];
};

/** 按提交人把可见文书收成组 */
const queueGroups = computed<QueueGroupView[]>(() => {
  const groups: QueueGroupView[] = [];
  const index = new Map<string, QueueGroupView>();
  for (const row of visibleQueueRows.value) {
    const submitter = submitterNameOf(row);
    let group = index.get(submitter);
    if (!group) {
      group = {
        key: submitter,
        name: submitter,
        cards: [],
      };
      index.set(submitter, group);
      groups.push(group);
    }
    group.cards.push(toQueueCard(row));
  }
  return groups;
});

/** 状态 code → 展示文案 */
function statusText(code?: string): string {
  return CASE_RECORD_STATUS_LABEL[code ?? ''] || code || '';
}

/** 列表短时间 MM-DD HH:mm */
function fmtShortTime(v?: string): string {
  if (!v) return '';
  const full = formatDateTime(v);
  const m = full.match(/(\d{2}-\d{2})\s+(\d{2}:\d{2})/);
  if (m) return `${m[1]} ${m[2]}`;
  return full;
}

/** 状态短标签 */
function statusShort(code?: string): string {
  if (code === 'PENDING_ARCHIVE') return '待审';
  if (code === 'DRAFT') return '草稿';
  if (code === 'ARCHIVED') return '已归档';
  return statusText(code);
}

/** 审核态：已归档为通过，驳回为驳回，其余待审 */
function reviewStatusOf(row: PendingArchiveListItemDto): ArchiveQueueReviewStatus {
  if (row.statusCode === 'ARCHIVED') return 'approved';
  if (row.statusCode === 'REJECTED') return 'rejected';
  return 'pending';
}

/** 色块按业务类别：门诊门、住院住、首页首 */
function docMarkOf(row: PendingArchiveListItemDto): string {
  if (row.bizCategory === 'op_document') return '门';
  if (row.bizCategory === 'case_homepage') return '首';
  return '住';
}

/** 色块样式 */
function docMarkClass(mark: string): string {
  if (mark === '门') return 'is-op';
  if (mark === '首') return 'is-home';
  return 'is-ip';
}

/** 提交人姓名；解析不到时归到未知 */
function submitterNameOf(row: PendingArchiveListItemDto): string {
  const name = row.submitterName?.trim();
  return name || '未知提交人';
}

/** 一行文书卡片；患者写在行上，提交人在组头。首页标题固定为病案首页，病案号在下一行 */
function toQueueCard(row: PendingArchiveListItemDto): QueueCard {
  const reviewStatus = reviewStatusOf(row);
  const mark = docMarkOf(row);
  const isHomepage = row.bizCategory === 'case_homepage';
  return {
    key: rowKeyOf(row),
    row,
    mark,
    markClass: docMarkClass(mark),
    title: isHomepage ? '病案首页' : row.title || '未命名文书',
    patientName: row.patientName || '',
    sub: isHomepage ? row.title || '' : fmtShortTime(row.submittedAt),
    reviewStatus,
    statusLabel: REVIEW_STATUS_LABEL[reviewStatus],
  };
}

/** 状态筛数量 */
function statusCountOf(value: QueueStatusFilter): number {
  return statusCounts.value[value];
}

/** 收起 / 展开一名提交人 */
function toggleGroup(key: string) {
  collapsedGroups.value = {
    ...collapsedGroups.value,
    [key]: !collapsedGroups.value[key],
  };
}

/** 选中当前筛下的第一条；没有则清空对比 */
async function selectFirstVisible() {
  const first = visibleQueueRows.value[0];
  if (first) {
    await selectRow(first);
    return;
  }
  activeBizId.value = '';
  clearDetail();
}

/** 拉待审；类型为全部时三类各查一页再合并 */
async function loadList() {
  listLoading.value = true;
  try {
    const categories =
      activeCategory.value === 'all' ? ARCHIVE_QUERY_CATEGORIES : [activeCategory.value];
    const keyword = queryParams.keyword?.trim() || undefined;
    const pages = await Promise.all(
      categories.map((bizCategory) =>
        pagePendingArchiveApi({
          pageNo: queryParams.pageNo,
          pageSize: queryParams.pageSize,
          bizCategory,
          keyword,
        }),
      ),
    );
    const rows: PendingArchiveListItemDto[] = [];
    for (const data of pages) {
      rows.push(...parsePagedRowsFromApiData<PendingArchiveListItemDto>(data).content);
    }
    tableData.value = rows;
    const stillVisible = visibleQueueRows.value.some((r) => rowKeyOf(r) === activeBizId.value);
    if (!stillVisible) {
      await selectFirstVisible();
    }
  } catch {
    ElMessage.warning('待审列表加载失败');
  } finally {
    listLoading.value = false;
  }
}

/** 清空对比区 */
function clearDetail() {
  detail.value = null;
  leftPlainText.value = '';
  rightPlainText.value = '';
  hasBaseline.value = false;
  leftLabel.value = '历史版本';
  rightLabel.value = '提交内容';
}

/** 选中待审行并加载对比 */
async function selectRow(row: PendingArchiveListItemDto) {
  const bizId = preserveApiIdForRequest(row.bizId);
  if (!bizId) {
    ElMessage.warning('业务主键缺失');
    return;
  }
  activeBizId.value = rowKeyOf(row);
  detailLoading.value = true;
  try {
    const dto = await getPendingArchiveDetailApi({
      bizCategory: row.bizCategory || '',
      bizId,
    });
    detail.value = dto;
    await buildComparePanes(dto, row);
    await nextTick();
    if (compareScrollRef.value) compareScrollRef.value.scrollTop = 0;
  } catch {
    clearDetail();
  } finally {
    detailLoading.value = false;
  }
}

/**
 * 文书：右侧=当前提交；左侧=最近版本快照（无则空）。
 * 首页：右侧=本次提交；左侧=上次同意归档的首页（首次归档无快照）。
 */
async function buildComparePanes(dto: PendingArchiveDetailDto, row: PendingArchiveListItemDto) {
  const category = (dto.bizCategory || row.bizCategory || '').trim();
  rightLabel.value = '提交内容';
  if (category === 'case_homepage') {
    rightPlainText.value = extractHomepageCompareText(dto.homepagePreviewJson);
    leftPlainText.value = extractHomepageCompareText(dto.homepageBaselineJson);
    hasBaseline.value = Boolean(leftPlainText.value);
    leftLabel.value = hasBaseline.value ? '上次归档' : '无历史版本快照';
    return;
  }

  rightPlainText.value = extractEmrPlainText(dto.documentContent);
  leftLabel.value = '历史版本';
  hasBaseline.value = false;
  leftPlainText.value = '';

  const documentId = preserveApiIdForRequest(dto.bizId || row.bizId);
  if (!documentId) return;

  try {
    const versions = (await getDocumentVersionListApi({ documentId })) ?? [];
    // 取最新版本号作为基线
    const sorted = [...versions].sort(
      (a, b) => (b.versionNumber ?? 0) - (a.versionNumber ?? 0),
    );
    const latest = sorted[0];
    if (!latest) {
      leftLabel.value = '无历史版本快照';
      return;
    }
    const versionContent = await getDocumentVersionContentApi({
      documentId,
      versionId: preserveApiIdForRequest(latest.id) || undefined,
      versionNumber: latest.versionNumber,
    });
    leftPlainText.value = extractEmrPlainText(versionContent?.content);
    hasBaseline.value = Boolean(leftPlainText.value);
    leftLabel.value = `历史版本 v${latest.versionNumber ?? '—'}`;
    if (!hasBaseline.value) {
      leftLabel.value = `历史版本 v${latest.versionNumber ?? '—'}（空）`;
    }
  } catch {
    leftLabel.value = '历史版本加载失败';
    leftPlainText.value = '';
    hasBaseline.value = false;
  }
}

/** 同意归档 */
async function handleApprove() {
  const row = activeRow.value;
  const bizId = preserveApiIdForRequest(row?.bizId || detail.value?.bizId);
  if (!bizId) {
    ElMessage.warning('请先选择待审记录');
    return;
  }
  if (!row || reviewStatusOf(row) !== 'pending') {
    ElMessage.warning('仅待审记录可以审批');
    return;
  }
  const bizCategory = row.bizCategory || detail.value?.bizCategory;
  if (!bizCategory) {
    ElMessage.warning('缺少业务类别');
    return;
  }
  try {
    await ElMessageBox.confirm('同意后将变为已归档，确认同意？', '同意归档', {
      type: 'warning',
      confirmButtonText: '同意',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    detailLoading.value = true;
    await approveArchiveApi({
      bizCategory,
      bizId,
    });
    ElMessage.success('已同意归档');
    await loadList();
  } catch {
    // 全局拦截器
  } finally {
    detailLoading.value = false;
  }
}

/** 打开驳回弹窗 */
function openReject() {
  if (!activeBizId.value) {
    ElMessage.warning('请先选择待审记录');
    return;
  }
  rejectReason.value = '';
  rejectDialogVisible.value = true;
}

/** 提交驳回 */
async function submitReject() {
  const reason = rejectReason.value.trim();
  if (!reason) {
    ElMessage.warning('请填写驳回原因');
    return;
  }
  const row = activeRow.value;
  const bizId = preserveApiIdForRequest(row?.bizId || detail.value?.bizId);
  if (!bizId) return;
  if (row && reviewStatusOf(row) !== 'pending') {
    ElMessage.warning('仅待审记录可以审批');
    return;
  }
  const bizCategory = row?.bizCategory || detail.value?.bizCategory;
  if (!bizCategory) {
    ElMessage.warning('缺少业务类别');
    return;
  }
  try {
    detailLoading.value = true;
    await rejectArchiveApi({
      bizCategory,
      bizId,
      rejectReason: reason,
    });
    ElMessage.success('已驳回归档');
    rejectDialogVisible.value = false;
    await loadList();
  } catch {
    // 全局拦截器
  } finally {
    detailLoading.value = false;
  }
}

/** 查询真实接口，并回到待审 */
function handleQuery() {
  queryParams.pageNo = 1;
  statusFilter.value = 'pending';
  loadList();
}

/** 检索回车重新查询 */
function handleSearchEnter() {
  handleQuery();
}

/** 清空检索并重新查询 */
function handleSearchClear() {
  queryParams.keyword = '';
  handleQuery();
}

/** 切换全部 / 待审 / 通过 / 驳回 */
async function switchStatus(code: QueueStatusFilter) {
  if (statusFilter.value === code) return;
  statusFilter.value = code;
  const stillVisible = visibleQueueRows.value.some((row) => rowKeyOf(row) === activeBizId.value);
  if (!stillVisible) {
    await selectFirstVisible();
  }
}

/** 切换全部 / 门诊病历 / 住院病历 / 病案首页 */
function switchCategory(code: QueueCategoryFilter) {
  if (activeCategory.value === code) return;
  activeCategory.value = code;
  statusFilter.value = 'pending';
  queryParams.pageNo = 1;
  loadList();
}

onMounted(() => {
  loadList();
});

onActivated(() => {
  loadList();
});
</script>

<template>
  <Page auto-content-height content-class="mrms-archive-approval-page">
    <div v-loading="listLoading || detailLoading" class="aa">
      <!-- 单画布：左队列 | 右对比 -->
      <div class="aa-body">
        <aside class="aa-queue" aria-label="待审队列">
          <div class="aa-queue__head">
            <span class="aa-queue__title">待审病历</span>
            <span class="aa-queue__badge">{{ statusCounts.all }}</span>
            <span class="aa-queue__progress">{{ statusCounts.reviewed }}/{{ statusCounts.all }} 已审</span>
          </div>
          <div class="aa-queue__cats" role="tablist">
            <button
              v-for="item in CATEGORY_FILTERS"
              :key="item.value"
              type="button"
              role="tab"
              class="aa-queue__cat"
              :class="{ 'is-active': activeCategory === item.value }"
              :aria-selected="activeCategory === item.value"
              @click="switchCategory(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
          <ElInput
            v-model="queryParams.keyword"
            class="aa-queue__search"
            clearable
            placeholder="检索姓名 / 住院号..."
            @clear="handleSearchClear"
            @keyup.enter="handleSearchEnter"
          />
          <div class="aa-queue__filters" role="tablist">
            <button
              v-for="item in STATUS_FILTERS"
              :key="item.value"
              type="button"
              role="tab"
              class="aa-queue__filter"
              :class="{ 'is-active': statusFilter === item.value }"
              :aria-selected="statusFilter === item.value"
              @click="switchStatus(item.value)"
            >
              {{ item.label }} {{ statusCountOf(item.value) }}
            </button>
          </div>
          <div class="aa-queue__list">
            <section v-for="group in queueGroups" :key="group.key" class="aa-pat-group">
              <button type="button" class="aa-pat" @click="toggleGroup(group.key)">
                <span class="aa-pat__chev" :class="{ 'is-off': collapsedGroups[group.key] }" />
                <span class="aa-pat__name">{{ group.name }}</span>
                <span class="aa-pat__no">{{ group.cards.length }}</span>
              </button>
              <div v-show="!collapsedGroups[group.key]">
                <button
                  v-for="card in group.cards"
                  :key="card.key"
                  type="button"
                  class="aa-doc"
                  :class="{ 'is-active': card.key === activeBizId }"
                  @click="selectRow(card.row)"
                >
                  <span class="aa-doc__mark" :class="card.markClass">{{ card.mark }}</span>
                  <span class="aa-doc__body">
                    <span class="aa-doc__line">
                      <span class="aa-doc__title">{{ card.title }}</span>
                      <span v-if="card.patientName" class="aa-doc__author">{{ card.patientName }}</span>
                    </span>
                    <span class="aa-doc__foot">
                      <span v-if="card.sub" class="aa-doc__sub">{{ card.sub }}</span>
                      <span class="aa-doc__status" :class="`is-${card.reviewStatus}`">
                        {{ card.statusLabel }}
                      </span>
                    </span>
                  </span>
                </button>
              </div>
            </section>
            <div v-if="!queueGroups.length" class="aa-queue__empty">暂无记录</div>
          </div>
        </aside>

        <section class="aa-work">
          <template v-if="hasSelection">
            <div class="aa-diff-head">
              <div class="aa-diff-head__col">
                <span class="aa-diff-head__tag is-old">历史</span>
                <span class="aa-diff-head__label">{{ leftLabel }}</span>
                <span v-if="!hasBaseline" class="aa-diff-head__hint">无基线</span>
              </div>
              <div class="aa-diff-head__col">
                <span class="aa-diff-head__tag is-new">提交</span>
                <span class="aa-diff-head__label">{{ rightLabel }}</span>
              </div>
            </div>

            <div ref="compareScrollRef" class="aa-diff-scroll">
              <div
                v-for="(row, idx) in diffRows"
                :key="idx"
                class="aa-diff-row"
                :class="`is-${row.rowKind}`"
              >
                <div
                  class="aa-diff-cell is-left"
                  :class="{ 'is-blank': !row.leftTokens.length }"
                >
                  <span class="aa-diff-no">{{ row.leftNo ?? '' }}</span>
                  <pre class="aa-diff-text"><template
                    v-for="(tok, ti) in row.leftTokens"
                    :key="ti"
                    ><span :class="`tok-${tok.kind}`">{{ tok.text }}</span></template
                  ><template v-if="!row.leftTokens.length">&nbsp;</template></pre>
                </div>
                <div
                  class="aa-diff-cell is-right"
                  :class="{ 'is-blank': !row.rightTokens.length }"
                >
                  <span class="aa-diff-no">{{ row.rightNo ?? '' }}</span>
                  <pre class="aa-diff-text"><template
                    v-for="(tok, ti) in row.rightTokens"
                    :key="ti"
                    ><span :class="`tok-${tok.kind}`">{{ tok.text }}</span></template
                  ><template v-if="!row.rightTokens.length">&nbsp;</template></pre>
                </div>
              </div>
              <div v-if="!diffRows.length" class="aa-work__empty-inner">暂无内容</div>
            </div>
          </template>

          <div v-else class="aa-work__empty">
            <ElEmpty description="从左侧选择一条待审记录" :image-size="72" />
          </div>
          <footer class="aa-work__ops">
            <ElButton type="danger" :disabled="!activeBizId" @click="openReject">驳回</ElButton>
            <ElButton type="primary" :disabled="!activeBizId" @click="handleApprove">
              同意归档
            </ElButton>
          </footer>
        </section>
      </div>
    </div>

    <ElDialog v-model="rejectDialogVisible" title="驳回归档" width="480px" destroy-on-close>
      <ElForm label-width="88px">
        <ElFormItem label="驳回原因" required>
          <ElInput
            v-model="rejectReason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写驳回原因"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="rejectDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="detailLoading" @click="submitReject">确定驳回</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>

<style scoped>
/* 单画布：无卡片间距、无双栏盒子 */
.aa {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
}

.aa-body {
  display: grid;
  grid-template-columns: 380px minmax(0, 1fr);
  flex: 1;
  min-height: 0;
}

.aa-queue {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid var(--el-border-color-extra-light);
  background: var(--el-bg-color, #fff);
}

.aa-queue__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 12px 4px;
}

.aa-queue__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.aa-queue__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.aa-queue__progress {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.aa-queue__cats {
  display: flex;
  margin: 10px 12px 0;
  padding: 3px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.aa-queue__cat {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  height: 34px;
  padding: 0 6px;
  border-radius: 6px;
  font-size: 14px;
  line-height: 34px;
  white-space: nowrap;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.aa-queue__cat.is-active {
  color: var(--el-color-primary);
  font-weight: 600;
  background: var(--el-bg-color);
  box-shadow: 0 1px 2px rgb(0 0 0 / 6%);
}

.aa-queue__search {
  box-sizing: border-box;
  width: calc(100% - 24px);
  margin: 8px 12px 0;
}

.aa-queue__filters {
  display: flex;
  margin: 8px 8px 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.aa-queue__filter {
  flex: 1;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  padding: 8px 0 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.aa-queue__filter.is-active {
  color: var(--el-color-primary);
  font-weight: 600;
  border-bottom-color: var(--el-color-primary);
}

.aa-queue__list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 4px 0 8px;
}

.aa-queue__empty {
  margin: 48px auto;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.aa-pat {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 12px 4px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.aa-pat:hover {
  background: var(--el-fill-color-light);
}

.aa-pat__chev {
  width: 0;
  height: 0;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
  border-left: 5px solid var(--el-text-color-secondary);
  transform: rotate(90deg);
  flex-shrink: 0;
}

.aa-pat__chev.is-off {
  transform: none;
}

.aa-pat__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.aa-pat__no {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aa-doc {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  padding: 8px 12px 8px 28px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.aa-doc:hover {
  background: var(--el-fill-color-light);
}

.aa-doc.is-active {
  background: var(--el-color-primary-light-9);
}

.aa-doc__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-top: 1px;
  border-radius: 4px;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-white, #fff);
}

.aa-doc__mark.is-op {
  background: var(--el-color-primary);
}

.aa-doc__mark.is-ip {
  background: var(--el-color-success);
}

.aa-doc__mark.is-home {
  background: color-mix(in srgb, var(--el-color-primary) 45%, var(--el-color-success));
}

.aa-doc__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.aa-doc__line {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.aa-doc__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.aa-doc__author {
  font-size: 12px;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aa-doc__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}

.aa-doc__sub {
  margin-right: auto;
  min-width: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aa-doc__status {
  flex-shrink: 0;
  height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 20px;
}

.aa-doc__status.is-pending {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.aa-doc__status.is-approved {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.aa-doc__status.is-rejected {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.aa-doc.is-active .aa-doc__status {
  background: var(--el-bg-color, #fff);
}

.aa-work {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--el-bg-color, #fff);
}

.aa-diff-head {
  display: grid;
  grid-template-columns: 1fr 1fr;
  flex-shrink: 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.aa-diff-head__col {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  min-width: 0;
}

.aa-diff-head__col + .aa-diff-head__col {
  border-left: 1px solid var(--el-border-color-extra-light);
}

.aa-diff-head__tag {
  flex-shrink: 0;
  height: 20px;
  padding: 0 6px;
  border-radius: 3px;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
}

.aa-diff-head__tag.is-old {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.aa-diff-head__tag.is-new {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.aa-diff-head__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aa-diff-head__hint {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.aa-diff-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.65;
}

.aa-diff-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 22px;
}

.aa-diff-row + .aa-diff-row {
  border-top: 1px solid transparent;
}

.aa-diff-cell {
  display: flex;
  align-items: stretch;
  min-width: 0;
}

.aa-diff-cell.is-right {
  border-left: 1px solid var(--el-border-color-extra-light);
}

.aa-diff-cell.is-blank {
  background: var(--el-fill-color-blank, #fafafa);
}

.aa-diff-row.is-remove .aa-diff-cell.is-left:not(.is-blank),
.aa-diff-row.is-change .aa-diff-cell.is-left:not(.is-blank) {
  background: color-mix(in srgb, var(--el-color-danger) 8%, transparent);
}

.aa-diff-row.is-add .aa-diff-cell.is-right:not(.is-blank),
.aa-diff-row.is-change .aa-diff-cell.is-right:not(.is-blank) {
  background: color-mix(in srgb, var(--el-color-success) 8%, transparent);
}

.aa-diff-no {
  flex: 0 0 36px;
  padding: 1px 6px;
  text-align: right;
  color: var(--el-text-color-placeholder);
  user-select: none;
  font-variant-numeric: tabular-nums;
  border-right: 1px solid var(--el-border-color-extra-light);
  background: transparent;
}

.aa-diff-text {
  flex: 1;
  margin: 0;
  padding: 1px 12px;
  white-space: pre-wrap;
  word-break: break-word;
  font: inherit;
  color: var(--el-text-color-primary);
}

.tok-add {
  background: color-mix(in srgb, var(--el-color-success) 22%, transparent);
  border-radius: 2px;
}

.tok-remove {
  background: color-mix(in srgb, var(--el-color-danger) 22%, transparent);
  border-radius: 2px;
  text-decoration: line-through;
}

.tok-same {
  background: transparent;
}

.aa-work__ops {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
  gap: 8px;
  margin-top: auto;
  padding: 10px 16px;
  border-top: 1px solid var(--el-border-color-extra-light);
  background: var(--el-bg-color, #fff);
}

.aa-work__empty {
  flex: 1;
  display: grid;
  place-items: center;
}

.aa-work__empty-inner {
  padding: 48px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 1280px) {
  .aa-body {
    grid-template-columns: 340px minmax(0, 1fr);
  }
}
</style>
