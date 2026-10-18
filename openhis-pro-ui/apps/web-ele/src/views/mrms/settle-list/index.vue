<script setup lang="ts">
/**
 * 结算清单独立只读预览：页签/列头对齐老系统 emrMainPage 结算清单
 *（诊断 drgdiagnoseGrid / 手术 InHosDrgOperation / 重症 icuGrid / 输血 bldGrid）。
 * 上传/提交/撤回/4103 复用 medcas-ops API，不嵌病案详情编辑。
 * 西医未对码时可从「疾病与诊断目录」选医保码，写回国临目录对照；
 * 中医不对国临→医保：国临列空、医保列回显落库码/名。
 */
import type { DiagnosisCatalogItem } from '#/api';
import type {
  LoadSettleListPreviewResult,
  SettleListDiagnosisPreviewDto,
  SettleListProcedurePreviewDto,
} from '#/api/mrms/settle-list';

import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@zxplat/common-ui';
import { useTabs } from '@zxplat/hooks';

import { ElButton, ElDialog, ElMessage, ElTabPane, ElTabs, ElTag } from 'element-plus';

import {
  createDiagnosisCatalogApi,
  getDiagnosisCatalogApi,
  pageDiagnosisCatalogApi,
  updateDiagnosisCatalogApi,
} from '#/api';
import { loadSettleListPreviewApi } from '#/api/mrms';
import {
  modifySettleListStatusApi,
  querySettleList4103Api,
  uploadSettleListApi,
  type SettleListQuery4103Dto,
  type UploadResultDto,
} from '#/chs/api/medcas-ops';
import SelectTable from '#/components/select-table.vue';
import { PublicationStatusCode } from '#/constants/domain/publication-status';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import MrmsSettleQuery4103Dialog from '../components/mrms-settle-query-4103-dialog.vue';
import { buildMrmsPatientTabTitle } from '../mrms.constants';
import { settleUploadStatusLabel } from '../homepage/homepage.constants';

defineOptions({ name: 'MrmsSettleListPreview' });

/** 疾病与诊断目录（医保 1307 主对照） */
const SOURCE_GENERAL_DX = 'general_dx';
/** 国家临床版 2.0 */
const SOURCE_CLINICAL_V2 = 'clinical_v2';
/** 中西医维度：中医 */
const DIAGNOSIS_KIND_TCM = 'TCM';

const route = useRoute();
const { closeCurrentTab, setTabTitle } = useTabs();

const loading = ref(false);
const acting = ref(false);
const activeTab = ref('diagnosis');
const preview = ref<LoadSettleListPreviewResult | null>(null);

/** 4103 查询结果弹窗 */
const query4103Visible = ref(false);
const query4103Result = ref<SettleListQuery4103Dto | null>(null);

/** 对码弹窗：当前诊断行 */
const mapDialogVisible = ref(false);
const mapActing = ref(false);
const mapTargetRow = ref<SettleListDiagnosisPreviewDto | null>(null);
const mapYbCode = ref<string | undefined>(undefined);
const mapYbLabel = ref('');

/** 对码选表列（对齐疾病目录编辑弹窗） */
const mapYbColumns = [
  { prop: 'conditionCode', label: '编码', width: 140 },
  { prop: 'name', label: '名称', minWidth: 220 },
  { prop: 'pyStr', label: '拼音', width: 100 },
];

/** 路由病案主键（雪花字符串） */
const routeCaseRecordId = computed(() => {
  const raw = route.params.caseRecordId;
  if (typeof raw === 'string' && raw.trim()) {
    return raw.trim();
  }
  return '';
});

/** 路由就诊主键（无病案时 query） */
const routeEncounterId = computed(() => {
  const raw = route.query.encounterId;
  if (typeof raw === 'string' && raw.trim()) {
    return raw.trim();
  }
  return '';
});

/** 当前可用于上报的病案主键 */
const effectiveCaseRecordId = computed(() => {
  const fromPreview = (preview.value?.caseRecordId ?? '').toString().trim();
  if (fromPreview) {
    return fromPreview;
  }
  return routeCaseRecordId.value;
});

/**
 * 按预览患者刷新页签标题（多开时区分）。
 */
function syncTabTitleFromPreview() {
  const p = preview.value;
  void setTabTitle(buildMrmsPatientTabTitle('结算清单', p?.psnName, p?.iptNo));
}

/**
 * 加载预览数据。
 */
async function loadPreview() {
  if (!routeCaseRecordId.value && !routeEncounterId.value) {
    ElMessage.warning('缺少病案或就诊主键');
    return;
  }
  loading.value = true;
  try {
    preview.value = await loadSettleListPreviewApi({
      caseRecordId: routeCaseRecordId.value || undefined,
      encounterId: routeEncounterId.value || undefined,
    });
    syncTabTitleFromPreview();
  } catch {
    // 全局拦截器提示
  } finally {
    loading.value = false;
  }
}

/**
 * 关闭当前结算清单页签（列表页签仍保留）。
 */
async function goBack() {
  await closeCurrentTab();
}

/**
 * 格式化上传结果提示。
 *
 * @param step 结果
 */
function formatUploadResult(step: UploadResultDto): string {
  if (step.skipped) {
    return '已跳过（此前已成功）';
  }
  if (step.statusCode === 'success') {
    return '成功';
  }
  return `失败 ${step.errMsg || step.infcode || step.statusCode || ''}`.trim();
}

/**
 * 上传 4101A 结算清单。
 */
async function handleUploadSettle() {
  const id = effectiveCaseRecordId.value;
  if (!id) {
    ElMessage.warning('缺少病案主键，请先完成建档');
    return;
  }
  acting.value = true;
  try {
    const result = await uploadSettleListApi({
      caseRecordId: preserveApiIdForRequest(id) ?? id,
    });
    ElMessage.success(`4101A 上传：${formatUploadResult(result)}`);
    await loadPreview();
  } catch {
    // 全局拦截器
  } finally {
    acting.value = false;
  }
}

/**
 * 4102 提交或撤回审核。
 *
 * @param stasType 1 提交 / 0 撤回
 */
async function handleModifyStatus(stasType: '0' | '1') {
  const id = effectiveCaseRecordId.value;
  if (!id) {
    ElMessage.warning('缺少病案主键');
    return;
  }
  const label = stasType === '1' ? '提交审核' : '撤回审核';
  acting.value = true;
  try {
    const result = await modifySettleListStatusApi({
      caseRecordId: preserveApiIdForRequest(id) ?? id,
      stasType,
    });
    ElMessage.success(`${label}：${formatUploadResult(result)}`);
    await loadPreview();
  } catch {
    // 全局拦截器
  } finally {
    acting.value = false;
  }
}

/**
 * 平台 4103 查询：弹可读摘要。
 */
async function handleQuery4103() {
  const id = effectiveCaseRecordId.value;
  if (!id) {
    ElMessage.warning('缺少病案主键');
    return;
  }
  acting.value = true;
  try {
    const data = await querySettleList4103Api({
      caseRecordId: preserveApiIdForRequest(id) ?? id,
    });
    if (!data.success) {
      ElMessage.error(data.errMsg || data.platformMsg || '4103 查询失败');
      return;
    }
    query4103Result.value = data;
    query4103Visible.value = true;
  } catch {
    // 全局拦截器
  } finally {
    acting.value = false;
  }
}

/**
 * 结算清单打印预留（不接医保本地客户端）。
 */
function handlePrintStub() {
  ElMessage.info('结算清单打印预留：走医保侧打印口径，本期未接本地客户端');
}

/**
 * 是否中医诊断：落库码即中医目录码，国临列空，医保列回显，不对国临→医保。
 *
 * @param row 诊断行
 */
function isDiagTcm(row: SettleListDiagnosisPreviewDto): boolean {
  return String(row.diagnosisKind || '').toUpperCase() === DIAGNOSIS_KIND_TCM;
}

/**
 * 是否入院诊断（4101A 不上送入院行；是否上传列显示 —）。
 *
 * @param row 诊断行
 */
function isAdmissionDiag(row: SettleListDiagnosisPreviewDto): boolean {
  const code = String(row.diagTypeCode || '');
  return code.startsWith('admission');
}

/**
 * 上传=否（needYbUp=0）时弱化行样式，提示不会进 4101A/4401。
 *
 * @param row 诊断行
 */
function isNeedYbUpSkipped(row: SettleListDiagnosisPreviewDto): boolean {
  if (isAdmissionDiag(row)) {
    return false;
  }
  return String(row.needYbUp ?? '').trim() === '0';
}

/**
 * 西医诊断且未对医保码（中医不标未对码）。
 *
 * @param row 诊断行
 */
function isDiagUnmapped(row: SettleListDiagnosisPreviewDto): boolean {
  if (isDiagTcm(row)) {
    return false;
  }
  return row.mapped === false;
}

/**
 * 西医未对码即可对码：有国临目录主键直接改；无主键则选码后新建国临行再绑。
 *
 * @param row 诊断行
 */
function canMapDiagYb(row: SettleListDiagnosisPreviewDto): boolean {
  if (isDiagTcm(row) || !isDiagUnmapped(row)) {
    return false;
  }
  return !!(row.clinicCode || '').toString().trim();
}

/**
 * 打开国临→医保对码弹窗。
 *
 * @param row 诊断行
 */
function openDiagYbMapDialog(row: SettleListDiagnosisPreviewDto) {
  if (!canMapDiagYb(row)) {
    ElMessage.warning('中医诊断无需对码，或缺少诊断编码');
    return;
  }
  mapTargetRow.value = row;
  mapYbCode.value = undefined;
  mapYbLabel.value = '';
  mapDialogVisible.value = true;
}

/**
 * 关闭对码弹窗。
 */
function closeDiagYbMapDialog() {
  mapDialogVisible.value = false;
}

/**
 * 弹窗关闭后清空对码选中态。
 */
function resetDiagYbMapState() {
  mapTargetRow.value = null;
  mapYbCode.value = undefined;
  mapYbLabel.value = '';
}

/**
 * 远程检索疾病与诊断目录（选医保码）。
 *
 * @param params 分页与关键字
 */
async function remoteSearchYbDx(params: Record<string, unknown>) {
  const response = await pageDiagnosisCatalogApi({
    pageNo: Number(params.pageNo ?? 1) || 1,
    pageSize: Number(params.pageSize ?? 20) || 20,
    keyword: String(params.keyword ?? '').trim() || undefined,
    sourceEnum: SOURCE_GENERAL_DX,
  });
  const parsed = parsePagedRowsFromApiData<DiagnosisCatalogItem>(response.data);
  const content = (parsed.content ?? []).map((item) => ({
    ...item,
    value: String(item.conditionCode ?? ''),
    label: String(item.name ?? ''),
  }));
  return { content, totalCount: parsed.totalCount };
}

/**
 * SelectTable 选中医保行：记下码与名称。
 *
 * @param item 选中行
 */
function onMapYbBeforeClose(item: Record<string, unknown>) {
  mapYbLabel.value = String(item.name ?? item.label ?? '');
  const code = String(item.ybNo ?? item.conditionCode ?? item.value ?? '').trim();
  mapYbCode.value = code || undefined;
  return true;
}

/**
 * SelectTable v-model 变更（编码）。
 *
 * @param code 医保编码
 */
function onMapYbCodePicked(code: string | string[]) {
  const v = Array.isArray(code) ? (code[0] ?? '') : code;
  mapYbCode.value = v || undefined;
}

/**
 * 确认对码：写回国临目录 yb_no（无国临行则先新建再绑），并刷新预览。
 */
async function confirmDiagYbMap() {
  const row = mapTargetRow.value;
  const clinicCode = (row?.clinicCode ?? '').toString().trim();
  const ybNo = (mapYbCode.value ?? '').trim();
  if (!row || !clinicCode) {
    ElMessage.warning('缺少诊断编码');
    return;
  }
  if (!ybNo) {
    ElMessage.warning('请选择医保编码');
    return;
  }
  mapActing.value = true;
  try {
    let defId = (row.clinicalDefinitionId ?? '').toString().trim();
    // 业务分支：首页码不在国临目录时，按编码+名称补建 clinical_v2 再绑医保
    if (!defId) {
      const diagName = (row.diagName ?? '').toString().trim() || clinicCode;
      const created = await createDiagnosisCatalogApi({
        sourceEnum: SOURCE_CLINICAL_V2,
        conditionCode: clinicCode,
        name: diagName,
        clinicCode,
        ybNo,
        ybFlag: 1,
        ybMatchFlag: 1,
        statusEnum: PublicationStatusCode.ACTIVE,
      });
      defId = String(created.data?.id ?? '').trim();
      if (!defId) {
        ElMessage.success('对码成功');
        closeDiagYbMapDialog();
        await loadPreview();
        return;
      }
    } else {
      const idForReq = preserveApiIdForRequest(defId) ?? defId;
      const detailRes = await getDiagnosisCatalogApi(idForReq);
      const detail = (detailRes.data || {}) as DiagnosisCatalogItem;
      await updateDiagnosisCatalogApi({
        ...detail,
        id: idForReq,
        clinicCode: String(detail.conditionCode ?? clinicCode).trim() || clinicCode,
        ybNo,
        ybFlag: 1,
        ybMatchFlag: 1,
      });
    }
    ElMessage.success('对码成功');
    closeDiagYbMapDialog();
    await loadPreview();
  } catch {
    // 全局拦截器提示
  } finally {
    mapActing.value = false;
  }
}

/**
 * 手术行是否未对医保码。
 *
 * @param row 手术行
 */
function isProcUnmapped(row: SettleListProcedurePreviewDto): boolean {
  return row.mapped === false;
}

/**
 * 入院病情码 → 老系统文案（1有/2未确定/3不明/4无）。
 *
 * @param code 入院病情码
 */
function formatAdmCond(code?: string): string {
  if (code === '1') {
    return '有';
  }
  if (code === '2') {
    return '未确定';
  }
  if (code === '3') {
    return '不明';
  }
  if (code === '4') {
    return '无';
  }
  return code || '';
}

/**
 * 病情码 → 老系统文案（1治愈/2好转/3未愈/4死亡/9其他）。
 *
 * @param code 病情码 outIllStatusCode
 */
function formatOutIllStatus(code?: string): string {
  if (code === '1') {
    return '治愈';
  }
  if (code === '2') {
    return '好转';
  }
  if (code === '3') {
    return '未愈';
  }
  if (code === '4') {
    return '死亡';
  }
  if (code === '9') {
    return '其他';
  }
  return code || '';
}

/**
 * 主手术标志 → 是/否。
 *
 * @param flag 主手术标志
 */
function formatMainOprn(flag?: string): string {
  if (flag === '1') {
    return '是';
  }
  if (flag === '0') {
    return '否';
  }
  return flag || '';
}

onMounted(() => {
  loadPreview();
});
</script>

<template>
  <Page auto-content-height content-class="p-3">
    <div v-loading="loading" class="settle-preview">
      <div class="settle-preview__toolbar">
        <div class="settle-preview__title">
          <span class="settle-preview__name">
            {{ preview?.psnName || '结算清单' }}
          </span>
          <span v-if="preview?.medcasno" class="settle-preview__meta">
            病案号 {{ preview.medcasno }}
          </span>
          <span v-if="preview?.iptNo" class="settle-preview__meta">
            住院号 {{ preview.iptNo }}
          </span>
          <ElTag v-if="preview?.settleUploadStatusCode" size="small" type="info">
            结算上传 {{ settleUploadStatusLabel(preview.settleUploadStatusCode) }}
          </ElTag>
          <ElTag v-if="preview?.psnNo" size="small" type="primary">
            人员编号 {{ preview.psnNo }}
          </ElTag>
          <ElTag v-if="preview?.setlId" size="small" type="primary">
            结算ID {{ preview.setlId }}
          </ElTag>
        </div>
        <div class="settle-preview__actions">
          <ElButton :loading="acting" type="primary" @click="handleUploadSettle">
            上传结算清单
          </ElButton>
          <ElButton :loading="acting" @click="handleModifyStatus('1')">提交审核</ElButton>
          <ElButton :loading="acting" @click="handleModifyStatus('0')">撤回审核</ElButton>
          <ElButton :loading="acting" @click="handleQuery4103">4103 查询</ElButton>
          <ElButton @click="handlePrintStub">打印（预留）</ElButton>
          <ElButton @click="goBack">关闭</ElButton>
        </div>
      </div>

      <div v-if="preview" class="settle-preview__summary">
        <span>科室：{{ preview.admCatyName || '—' }}</span>
        <span>入院：{{ preview.admTime || '—' }}</span>
        <span>出院：{{ preview.dscgTime || '—' }}</span>
        <span>
          护理天数 特级/一/二/三：
          {{ preview.spgaNurscareDays ?? '—' }}/{{ preview.lv1NurscareDays ?? '—' }}/{{
            preview.scdNurscareDays ?? '—'
          }}/{{ preview.lv3NurscareDays ?? '—' }}
        </span>
      </div>

      <ElTabs v-model="activeTab" class="settle-preview__tabs">
        <!-- 列对齐老 emrMainPage drgdiagnoseGrid -->
        <ElTabPane label="诊断信息" name="diagnosis">
          <div class="settle-preview__table-wrap">
            <table class="settle-preview__table">
              <thead>
                <tr>
                  <th>诊断类型</th>
                  <th>中西医</th>
                  <th>国临诊断名称</th>
                  <th>国临码</th>
                  <th>医保编码</th>
                  <th>医保名称</th>
                  <th>入院病情</th>
                  <th>是否上传</th>
                  <th>病情</th>
                  <th>确诊时间</th>
                  <th>说明</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(row, idx) in preview?.diagnoses || []"
                  :key="`d-${idx}`"
                  :class="{ 'is-skip-upload': isNeedYbUpSkipped(row) }"
                >
                  <td>{{ row.diagTypeLabel || row.diagTypeCode || '' }}</td>
                  <td>{{ row.diagnosisKindLabel || '' }}</td>
                  <!-- 中医：国临列空；西医展示国临 -->
                  <td>
                    <template v-if="isDiagTcm(row)">—</template>
                    <template v-else>{{ row.diagName || '' }}</template>
                  </td>
                  <td>
                    <template v-if="isDiagTcm(row)">—</template>
                    <template v-else>{{ row.clinicCode || '' }}</template>
                  </td>
                  <td :class="{ 'is-unmapped': isDiagUnmapped(row) }">
                    <!-- 中医落库即目录码；西医已对码显示 ybNo，未对码引导对码 -->
                    <template v-if="isDiagTcm(row)">
                      {{ row.ybNo || '' }}
                    </template>
                    <template v-else-if="!isDiagUnmapped(row)">
                      {{ row.ybNo || '' }}
                    </template>
                    <template v-else>
                      <span class="settle-preview__unmapped-text">未对码</span>
                      <ElButton
                        v-if="canMapDiagYb(row)"
                        class="settle-preview__map-btn"
                        link
                        type="primary"
                        @click="openDiagYbMapDialog(row)"
                      >
                        对码
                      </ElButton>
                    </template>
                  </td>
                  <td>
                    <template v-if="isDiagTcm(row)">
                      {{ row.ybName || '' }}
                    </template>
                    <template v-else-if="!isDiagUnmapped(row)">
                      {{ row.ybName || '' }}
                    </template>
                  </td>
                  <td>{{ formatAdmCond(row.admCondCode) }}</td>
                  <td>
                    <!-- 入院不上 4101A；出院按 needYbUp 码展示（0=否） -->
                    <template v-if="isAdmissionDiag(row)">—</template>
                    <ElTag
                      v-else-if="isNeedYbUpSkipped(row)"
                      size="small"
                      effect="light"
                      type="info"
                    >
                      否
                    </ElTag>
                    <ElTag v-else size="small" effect="light" type="success">是</ElTag>
                  </td>
                  <td>{{ formatOutIllStatus(row.outIllStatusCode) }}</td>
                  <td>{{ row.confirmTime || '' }}</td>
                  <td>{{ row.diagRemark || '' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </ElTabPane>

        <!-- 列对齐老 InHosDrgOperation operationGrid2（只读预览，无操作列） -->
        <ElTabPane label="手术记录" name="procedure">
          <div class="settle-preview__table-wrap">
            <table class="settle-preview__table settle-preview__table--wide">
              <thead>
                <tr>
                  <th>手术开始时间</th>
                  <th>手术结束时间</th>
                  <th>手术编码</th>
                  <th>手术项目</th>
                  <th>医保编码</th>
                  <th>医保项目</th>
                  <th>DRG编码名称</th>
                  <th>DRG名称</th>
                  <th>主手术</th>
                  <th>切口类别</th>
                  <th>手术级别</th>
                  <th>切口愈合等级</th>
                  <th>送检</th>
                  <th>样本号</th>
                  <th>备注</th>
                  <th>签名</th>
                  <th>手术医生</th>
                  <th>配合护士</th>
                  <th>Ⅰ助</th>
                  <th>Ⅱ助</th>
                  <th>麻醉方式</th>
                  <th>麻醉医师</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, idx) in preview?.procedures || []" :key="`p-${idx}`">
                  <td>{{ row.oprnDate || '' }}</td>
                  <td />
                  <td>{{ row.clinicCode || '' }}</td>
                  <td>{{ row.oprnOprtName || '' }}</td>
                  <td :class="{ 'is-unmapped': isProcUnmapped(row) }">
                    {{ row.ybNo || '' }}
                  </td>
                  <td />
                  <td>{{ row.drgExtCode || '' }}</td>
                  <td />
                  <td>{{ formatMainOprn(row.mainOprnFlag) }}</td>
                  <td>{{ row.oprnIncisionCode || '' }}</td>
                  <td>{{ row.oprnLevelCode || '' }}</td>
                  <td>{{ row.healGradeCode || '' }}</td>
                  <td />
                  <td />
                  <td />
                  <td />
                  <td>{{ row.operDrName || '' }}</td>
                  <td />
                  <td />
                  <td />
                  <td>{{ row.anstWayCode || '' }}</td>
                  <td>{{ row.anstDrName || '' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </ElTabPane>

        <!-- 列对齐老 icuGrid；数据来自首页 mrms_homepage_icu -->
        <ElTabPane label="重症记录" name="icu">
          <div class="settle-preview__table-wrap">
            <table class="settle-preview__table">
              <thead>
                <tr>
                  <th>重症监护病房类型</th>
                  <th>重症监护进入时间</th>
                  <th>重症监护退出时间</th>
                  <th>重症监护合计时长(天/时/分)</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(row, idx) in (preview?.icuinfo as Record<string, string>[] | undefined) || []"
                  :key="`icu-${idx}`"
                >
                  <td>{{ row.scs_cutd_ward_type || row.scsCutdWardType || '' }}</td>
                  <td>{{ row.scs_cutd_inpool_time || row.scsCutdInpoolTime || '' }}</td>
                  <td>{{ row.scs_cutd_exit_time || row.scsCutdExitTime || '' }}</td>
                  <td>{{ row.scs_cutd_sum_dura || row.scsCutdSumDura || '' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </ElTabPane>

        <!-- 列对齐老 bldGrid；数据来自首页 mrms_homepage_bld -->
        <ElTabPane label="输血记录" name="blood">
          <div class="settle-preview__table-wrap">
            <table class="settle-preview__table">
              <thead>
                <tr>
                  <th>输血品种</th>
                  <th>输血量</th>
                  <th>输血计量单位</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(row, idx) in (preview?.bldinfo as Record<string, string>[] | undefined) || []"
                  :key="`bld-${idx}`"
                >
                  <td>{{ row.bld_cat || row.bldCat || '' }}</td>
                  <td>{{ row.bld_amt || row.bldAmt || '' }}</td>
                  <td>{{ row.bld_unt || row.bldUnt || '' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </ElTabPane>
      </ElTabs>
    </div>

    <ElDialog
      v-model="mapDialogVisible"
      title="国临对医保码"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
      @closed="resetDiagYbMapState"
    >
      <p class="settle-preview__map-hint">
        国临
        {{ mapTargetRow?.clinicCode || '' }}
        {{ mapTargetRow?.diagName ? `（${mapTargetRow.diagName}）` : '' }}
        → 请从疾病与诊断目录选择医保编码，保存后建立对照。
      </p>
      <div class="settle-preview__map-picker">
        <SelectTable
          :model-value="mapYbCode"
          :selected-label="mapYbLabel"
          placeholder="从「疾病与诊断目录」选择医保编码"
          :remote-method="remoteSearchYbDx"
          :columns="mapYbColumns"
          value-key="conditionCode"
          label-key="name"
          :panel-width="680"
          :before-close="onMapYbBeforeClose"
          @update:model-value="onMapYbCodePicked"
        />
      </div>
      <template #footer>
        <ElButton @click="closeDiagYbMapDialog">取消</ElButton>
        <ElButton :loading="mapActing" type="primary" @click="confirmDiagYbMap"> 确定 </ElButton>
      </template>
    </ElDialog>
    <MrmsSettleQuery4103Dialog v-model="query4103Visible" :result="query4103Result" />
  </Page>
</template>

<style scoped>
.settle-preview__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.settle-preview__title {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.settle-preview__name {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.settle-preview__meta {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.settle-preview__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.settle-preview__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.settle-preview__table-wrap {
  width: 100%;
  overflow-x: auto;
}

.settle-preview__table {
  width: 100%;
  min-width: 720px;
  border-collapse: collapse;
  font-size: 13px;
}

.settle-preview__table--wide {
  min-width: 1800px;
}

.settle-preview__table th,
.settle-preview__table td {
  padding: 8px 10px;
  text-align: center;
  border: 1px solid var(--el-border-color-lighter);
  white-space: nowrap;
}

.settle-preview__table th {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.settle-preview__table .is-unmapped {
  color: var(--el-color-warning);
}

.settle-preview__table tr.is-skip-upload td {
  color: var(--el-text-color-secondary);
  opacity: 0.78;
}

.settle-preview__unmapped-text {
  margin-right: 6px;
}

.settle-preview__map-btn {
  vertical-align: baseline;
}

.settle-preview__map-hint {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
}

.settle-preview__map-picker {
  width: 100%;
}
</style>
