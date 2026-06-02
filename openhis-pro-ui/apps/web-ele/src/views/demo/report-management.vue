<script setup lang="ts">
import { computed, ref } from 'vue';

defineOptions({ name: 'ReportManagementDemoPage' });

type HealthLevel = 'mild' | 'moderate' | 'normal' | 'severe';
type TagCategory = 'chronic' | 'organ' | 'scale';
type CareLevel = 'fullSelfCare' | 'fullyDisabled' | 'semiDisabled';

interface ReportTag {
  id: string;
  label: string;
  category: TagCategory;
  level: HealthLevel;
  source: 'auto' | 'manual';
}

interface OrganMarker {
  id: string;
  name: string;
  value: string;
  level: HealthLevel;
  x: string;
  y: string;
}

interface MonitorMarker {
  id: string;
  name: string;
  value: string;
  level: HealthLevel;
  x: string;
  y: string;
}

interface SideSystem {
  id: string;
  title: string;
  value: string;
  level: HealthLevel;
}

interface MealPlanItem {
  id: string;
  name: string;
  ingredients: string;
  focus: string;
}

interface NursingPathItem {
  id: string;
  stepName: string;
  focus: string;
  frequency: string;
}

const reportMeta = {
  elderName: '王淑兰',
  gender: '女',
  age: 82,
  careType: '半失能',
  roomNo: 'A-302',
  checkDate: '2026-04-21 09:30:00',
  markerName: '陈护士',
  scaleScore: '78/100',
};

const manualTagInput = ref('');
const manualTagCategory = ref<TagCategory>('organ');
const tagCategoryFilter = ref<'all' | TagCategory>('all');
const tagDialogVisible = ref(false);
const recommendationDialogVisible = ref(false);
const activeCareLevel = ref<CareLevel>('semiDisabled');

const manualTags = ref<ReportTag[]>([
  {
    id: 'manual-1',
    label: '压疮风险低',
    category: 'scale',
    level: 'normal',
    source: 'manual',
  },
]);

const leftSystems: SideSystem[] = [
  { id: '1', title: '神经系统', value: '认知评估 26 分', level: 'normal' },
  { id: '2', title: '呼吸系统', value: '呼吸 18 次/分', level: 'normal' },
  { id: '3', title: '心血管系统', value: '心率 72 次/分', level: 'normal' },
  { id: '4', title: '骨骼系统', value: '肌力轻度下降', level: 'mild' },
];

const rightSystems: SideSystem[] = [
  { id: '1', title: '消化系统', value: '消化功能稳定', level: 'normal' },
  { id: '2', title: '泌尿系统', value: '肌酐 130 μmol/L', level: 'moderate' },
  { id: '3', title: '内分泌代谢系统', value: '血糖 6.8 mmol/L', level: 'mild' },
  { id: '4', title: '生殖系统', value: '基础指标平稳', level: 'normal' },
];

const organMarkers: OrganMarker[] = [
  { id: 'heart', name: '心', value: '72 次/分', level: 'normal', x: '53%', y: '30%' },
  { id: 'lung', name: '肺', value: '18 次/分', level: 'normal', x: '47%', y: '36%' },
  { id: 'liver', name: '肝', value: '58 U/L', level: 'mild', x: '57%', y: '45%' },
  { id: 'spleen', name: '脾', value: '状态正常', level: 'normal', x: '44%', y: '50%' },
  { id: 'kidney', name: '肾', value: '130 μmol/L', level: 'moderate', x: '52%', y: '58%' },
];

const monitorMarkers: MonitorMarker[] = [
  { id: 'pressure', name: '血压', value: '125/80mmHg', level: 'normal', x: '22%', y: '48%' },
  { id: 'sugar', name: '血糖', value: '6.8mmol/L', level: 'mild', x: '74%', y: '56%' },
  { id: 'arm', name: '手臂活动', value: '轻度受限', level: 'mild', x: '18%', y: '63%' },
  { id: 'knee', name: '膝关节', value: '中度受限', level: 'moderate', x: '66%', y: '78%' },
];

const levelLegend = [
  { level: 'normal' as const, label: '正常', symbol: '●' },
  { level: 'mild' as const, label: '轻度异常', symbol: '△' },
  { level: 'moderate' as const, label: '中度异常', symbol: '▲' },
  { level: 'severe' as const, label: '重度异常', symbol: '!' },
];

const categoryLegend: Record<TagCategory, string> = {
  organ: '五脏类',
  chronic: '慢病类',
  scale: '量表类',
};

const careLevelLabelMap: Record<CareLevel, string> = {
  fullSelfCare: '全自理',
  semiDisabled: '半失能',
  fullyDisabled: '全失能',
};

const careLevelSummaryMap: Record<CareLevel, string> = {
  fullSelfCare: '强调慢病自我管理能力与功能维护，适合可自主完成日常活动老人。',
  semiDisabled: '强调辅助照护与活动能力维持，适合需要部分生活协助老人。',
  fullyDisabled: '强调安全照护与并发症预防，适合高度依赖照护老人。',
};

const baseMealPlanMap: Record<CareLevel, MealPlanItem[]> = {
  fullSelfCare: [
    {
      id: 'full-meal-1',
      name: '枸杞菊花茶',
      ingredients: '枸杞 10g、菊花 6g（可根据库存替换为桑叶）',
      focus: '疏肝明目，适合轻度肝功能偏颇且具备自主饮用能力老人',
    },
    {
      id: 'full-meal-2',
      name: '山药薏米粥',
      ingredients: '山药 40g、薏米 20g、大米 50g',
      focus: '健脾利湿，兼顾血糖波动期营养均衡',
    },
  ],
  semiDisabled: [
    {
      id: 'semi-meal-1',
      name: '黄芪瘦肉汤',
      ingredients: '黄芪 15g、瘦肉 80g、胡萝卜 50g',
      focus: '补气扶正，适配活动受限老人恢复期支持',
    },
    {
      id: 'semi-meal-2',
      name: '黑豆核桃糊',
      ingredients: '黑豆 30g、核桃仁 10g（可按库存替换芝麻）',
      focus: '补肾益脑，兼顾肾功能偏颇与吞咽友好性',
    },
  ],
  fullyDisabled: [
    {
      id: 'disabled-meal-1',
      name: '百合银耳羹',
      ingredients: '百合 12g、银耳 10g、红枣 3 枚',
      focus: '润肺安神，适合卧床老人夜间睡眠支持',
    },
    {
      id: 'disabled-meal-2',
      name: '南瓜小米泥',
      ingredients: '南瓜 60g、小米 30g（可做糊状喂养）',
      focus: '易消化高能量，降低喂养负担并减少误吸风险',
    },
  ],
};

const baseNursingPathMap: Record<CareLevel, NursingPathItem[]> = {
  fullSelfCare: [
    {
      id: 'full-path-1',
      stepName: '晨间自测引导',
      focus: '指导老人自测血糖、血压并记录异常值',
      frequency: '每日 1 次',
    },
    {
      id: 'full-path-2',
      stepName: '功能训练复核',
      focus: '复核下肢关节活动度，预防退行性下降',
      frequency: '每周 3 次',
    },
  ],
  semiDisabled: [
    {
      id: 'semi-path-1',
      stepName: '辅助移位与步态训练',
      focus: '护理员辅助起立、转移与短距离步行，防跌倒',
      frequency: '每日 2 次',
    },
    {
      id: 'semi-path-2',
      stepName: '代谢指标追踪',
      focus: '监测血糖、肾功能趋势，异常及时上报医生',
      frequency: '每日 1 次',
    },
  ],
  fullyDisabled: [
    {
      id: 'disabled-path-1',
      stepName: '体位管理',
      focus: '两小时翻身一次，联合皮肤评估预防压疮',
      frequency: '24 小时持续',
    },
    {
      id: 'disabled-path-2',
      stepName: '喂养与吞咽风险管理',
      focus: '喂养前后体位管理与口腔护理，减少误吸风险',
      frequency: '每次喂养执行',
    },
  ],
};

const mealPlanDraftMap = ref<Record<CareLevel, MealPlanItem[]>>({
  fullSelfCare: baseMealPlanMap.fullSelfCare.map((item) => ({ ...item })),
  semiDisabled: baseMealPlanMap.semiDisabled.map((item) => ({ ...item })),
  fullyDisabled: baseMealPlanMap.fullyDisabled.map((item) => ({ ...item })),
});

const nursingPathDraftMap = ref<Record<CareLevel, NursingPathItem[]>>({
  fullSelfCare: baseNursingPathMap.fullSelfCare.map((item) => ({ ...item })),
  semiDisabled: baseNursingPathMap.semiDisabled.map((item) => ({ ...item })),
  fullyDisabled: baseNursingPathMap.fullyDisabled.map((item) => ({ ...item })),
});

// 根据器官与监测点状态生成系统推荐标签，减少人工重复标记。
const autoMatchedTags = computed<ReportTag[]>(() => {
  const tags: ReportTag[] = [];
  const liverMarker = organMarkers.find((item) => item.id === 'liver');
  const kidneyMarker = organMarkers.find((item) => item.id === 'kidney');
  const sugarMarker = monitorMarkers.find((item) => item.id === 'sugar');

  if (liverMarker && liverMarker.level !== 'normal') {
    tags.push({
      id: 'auto-liver',
      label: `肝功能${liverMarker.level === 'mild' ? '轻度' : '中度'}偏颇`,
      category: 'organ',
      level: liverMarker.level,
      source: 'auto',
    });
  }
  if (kidneyMarker && kidneyMarker.level !== 'normal') {
    tags.push({
      id: 'auto-kidney',
      label: `肾功能${kidneyMarker.level === 'mild' ? '轻度' : '中度'}偏颇`,
      category: 'organ',
      level: kidneyMarker.level,
      source: 'auto',
    });
  }
  if (sugarMarker && sugarMarker.level !== 'normal') {
    tags.push({
      id: 'auto-sugar',
      label: `血糖${sugarMarker.level === 'mild' ? '轻度异常' : '中度异常'}`,
      category: 'chronic',
      level: sugarMarker.level,
      source: 'auto',
    });
  }

  const score = Number(reportMeta.scaleScore.split('/')[0] ?? 0);
  tags.push({
    id: 'auto-cognition',
    label: score >= 75 ? '认知功能良好' : '认知功能需关注',
    category: 'scale',
    level: score >= 75 ? 'normal' : 'mild',
    source: 'auto',
  });
  return tags;
});

// 合并自动与手工标签并去重，保证报告展示与管理区数据一致。
const mergedTags = computed<ReportTag[]>(() => {
  const allTags = [...autoMatchedTags.value, ...manualTags.value];
  const uniqueMap = new Map<string, ReportTag>();
  allTags.forEach((item) => {
    uniqueMap.set(`${item.category}-${item.label}`, item);
  });
  return [...uniqueMap.values()];
});

// 根据分类筛选标签，支持护理人员快速定位目标标签。
const filteredTags = computed(() => {
  if (tagCategoryFilter.value === 'all') {
    return mergedTags.value;
  }
  return mergedTags.value.filter((item) => item.category === tagCategoryFilter.value);
});

// 添加手工标签，补充系统自动匹配不到的个体化观察结果。
function handleAddManualTag() {
  const label = manualTagInput.value.trim();
  if (!label) {
    return;
  }
  manualTags.value.push({
    id: `manual-${Date.now()}`,
    label,
    category: manualTagCategory.value,
    level: 'mild',
    source: 'manual',
  });
  manualTagInput.value = '';
}

// 删除手工标签，自动标签不允许在前端直接删除。
function handleDeleteTag(tag: ReportTag) {
  if (tag.source === 'auto') {
    return;
  }
  manualTags.value = manualTags.value.filter((item) => item.id !== tag.id);
}

// 根据状态等级返回提示符号，用于器官与监测点右上角提醒。
function getLevelSymbol(level: HealthLevel) {
  if (level === 'mild') {
    return '△';
  }
  if (level === 'moderate') {
    return '▲';
  }
  if (level === 'severe') {
    return '!';
  }
  return '';
}

// 打开推荐弹窗并按当前照护类型定位默认推荐侧重点。
function openRecommendationDialog() {
  if (reportMeta.careType === '全自理') {
    activeCareLevel.value = 'fullSelfCare';
  } else if (reportMeta.careType === '全失能') {
    activeCareLevel.value = 'fullyDisabled';
  } else {
    activeCareLevel.value = 'semiDisabled';
  }
  recommendationDialogVisible.value = true;
}

// 在当前照护等级下新增药膳方案，便于根据机构库存快速补充。
function handleAddMealPlan() {
  mealPlanDraftMap.value[activeCareLevel.value].push({
    id: `meal-${Date.now()}`,
    name: '',
    ingredients: '',
    focus: '',
  });
}

// 删除当前照护等级下的药膳方案，保留护理人员调整自由度。
function handleRemoveMealPlan(itemId: string) {
  mealPlanDraftMap.value[activeCareLevel.value] = mealPlanDraftMap.value[
    activeCareLevel.value
  ].filter((item) => item.id !== itemId);
}

// 在当前照护等级下新增护理路径步骤，适配机构照护条件差异。
function handleAddNursingPath() {
  nursingPathDraftMap.value[activeCareLevel.value].push({
    id: `path-${Date.now()}`,
    stepName: '',
    focus: '',
    frequency: '',
  });
}

// 删除当前照护等级下护理步骤，支持基于照护能力精简路径。
function handleRemoveNursingPath(itemId: string) {
  nursingPathDraftMap.value[activeCareLevel.value] = nursingPathDraftMap.value[
    activeCareLevel.value
  ].filter((item) => item.id !== itemId);
}

// 重置当前照护等级草稿内容，便于快速回到系统推荐模板。
function handleResetCurrentLevelRecommendation() {
  mealPlanDraftMap.value[activeCareLevel.value] = baseMealPlanMap[activeCareLevel.value].map(
    (item) => ({ ...item }),
  );
  nursingPathDraftMap.value[activeCareLevel.value] = baseNursingPathMap[activeCareLevel.value].map(
    (item) => ({ ...item }),
  );
}
</script>

<template>
  <div class="report-page">
    <header class="report-header">
      <div class="title-wrap">
        <h2 class="report-title">养老院老人健康画像报告</h2>
        <p class="report-subtitle">图示仅展示健康数据状态，不体现疾病名称</p>
      </div>
      <div class="meta-wrap">
        <span>姓名：{{ reportMeta.elderName }}</span>
        <span>性别：{{ reportMeta.gender }}</span>
        <span>年龄：{{ reportMeta.age }} 岁</span>
        <span>照护类型：{{ reportMeta.careType }}</span>
        <ElButton type="primary" class="manage-tag-btn" @click="tagDialogVisible = true">
          标签管理
        </ElButton>
        <ElButton plain @click="openRecommendationDialog">药膳与护理路径推荐</ElButton>
      </div>
    </header>

    <section class="legend">
      <div v-for="item in levelLegend" :key="item.level" class="legend-item">
        <span class="legend-dot" :class="item.level">{{ item.symbol }}</span>
        <span>{{ item.label }}</span>
      </div>
    </section>

    <section class="overview-layout">
      <div class="overview-left">
        <section class="portrait-layout">
          <div class="mobile-preview-panel">
            <div class="mobile-side-note">
              <article
                v-for="item in leftSystems"
                :key="item.id"
                class="system-card compact"
                :class="item.level"
              >
                <h4>{{ item.title }}</h4>
                <p>{{ item.value }}</p>
              </article>
            </div>
          </div>

          <div class="body-panel">
            <div class="body-outline">
              <div class="head"></div>
              <div class="neck"></div>
              <div class="shoulder"></div>
              <div class="torso"></div>
              <div class="pelvis"></div>
              <div class="arm upper left"></div>
              <div class="arm upper right"></div>
              <div class="arm lower left"></div>
              <div class="arm lower right"></div>
              <div class="hand left"></div>
              <div class="hand right"></div>
              <div class="leg thigh left"></div>
              <div class="leg thigh right"></div>
              <div class="leg calf left"></div>
              <div class="leg calf right"></div>
              <div class="foot left"></div>
              <div class="foot right"></div>
              <div class="body-label left-arm">左上肢</div>
              <div class="body-label right-arm">右上肢</div>
              <div class="body-label left-leg">左下肢</div>
              <div class="body-label right-leg">右下肢</div>
              <div class="body-label trunk">躯干</div>

              <div
                v-for="item in organMarkers"
                :key="item.id"
                class="marker organ"
                :class="item.level"
                :style="{ left: item.x, top: item.y }"
              >
                <span class="name">{{ item.name }}</span>
                <span class="value">{{ item.value }}</span>
                <i v-if="getLevelSymbol(item.level)" class="symbol">
                  {{ getLevelSymbol(item.level) }}
                </i>
              </div>

              <div
                v-for="item in monitorMarkers"
                :key="item.id"
                class="marker monitor"
                :class="item.level"
                :style="{ left: item.x, top: item.y }"
              >
                <span class="name">{{ item.name }}</span>
                <span class="value">{{ item.value }}</span>
                <i v-if="getLevelSymbol(item.level)" class="symbol">
                  {{ getLevelSymbol(item.level) }}
                </i>
              </div>
            </div>
          </div>

          <div class="mobile-preview-panel">
            <div class="mobile-side-note">
              <article
                v-for="item in rightSystems"
                :key="item.id"
                class="system-card compact"
                :class="item.level"
              >
                <h4>{{ item.title }}</h4>
                <p>{{ item.value }}</p>
              </article>
            </div>
          </div>
        </section>
      </div>

      <div class="overview-right">
        <h3 class="mobile-preview-title">手机 APP 报告页设计图</h3>
        <div class="phone-shell">
          <div class="phone-notch"></div>
          <div class="phone-screen">
            <div class="app-header">
              <span>老人健康报告</span>
              <span>{{ reportMeta.checkDate.slice(5, 10) }}</span>
            </div>
            <div class="app-profile-card">
              <div class="profile-line">
                <strong>{{ reportMeta.elderName }}</strong>
                <span>{{ reportMeta.age }} 岁 / {{ reportMeta.gender }}</span>
              </div>
              <div class="profile-line">
                <span>照护等级：{{ reportMeta.careType }}</span>
                <span>量表：{{ reportMeta.scaleScore }}</span>
              </div>
            </div>
            <div class="app-section">
              <div class="section-title">健康标签</div>
              <div class="app-tag-list">
                <span v-for="tag in mergedTags.slice(0, 4)" :key="tag.id" class="app-tag">
                  {{ tag.label }}
                </span>
              </div>
            </div>
            <div class="app-section">
              <div class="section-title">药膳推荐</div>
              <div class="app-item">
                枸杞菊花茶 / 山药薏米粥
                <small>依据：肝肾偏颇 + 代谢管理</small>
              </div>
            </div>
            <div class="app-section">
              <div class="section-title">护理路径</div>
              <div class="app-item">
                监测 - 训练 - 复评
                <small>每日监测 1 次，每周复评</small>
              </div>
            </div>
            <div class="app-bottom-bar">
              <span>首页</span>
              <span class="active">报告</span>
              <span>我的</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="report-content">
      <div class="content-card">
        <h3>健康标签</h3>
        <div class="tag-list">
          <span v-for="tag in mergedTags" :key="tag.id" class="tag-item" :class="tag.level">
            {{ tag.label }}
          </span>
        </div>
      </div>

      <div class="content-card">
        <h3>数据说明</h3>
        <p>
          心功能正常，肺功能正常，肝功能轻度偏颇，肾功能中度偏颇，血压正常，血糖轻度异常。结合半失能照护特点，需持续关注肢体活动能力与代谢相关指标变化。
        </p>
      </div>

      <div class="content-card">
        <h3>药膳与护理建议</h3>
        <p>
          药膳建议：枸杞菊花茶（适配肝肾功能偏颇）。护理建议：每日监测血糖 1
          次，每周复评肝肾相关指标与肢体活动度。
        </p>
      </div>
    </section>

    <footer class="report-footer">
      <span>房间号：{{ reportMeta.roomNo }}</span>
      <span>检查日期：{{ reportMeta.checkDate }}</span>
      <span>医护标记人：{{ reportMeta.markerName }}</span>
      <span>量表得分：{{ reportMeta.scaleScore }}</span>
    </footer>

    <ElDialog
      v-model="tagDialogVisible"
      title="健康标签管理"
      width="760px"
      destroy-on-close
      align-center
    >
      <div class="tag-management-filter">
        <span class="filter-label">分类筛选</span>
        <button
          type="button"
          class="filter-btn"
          :class="{ active: tagCategoryFilter === 'all' }"
          @click="tagCategoryFilter = 'all'"
        >
          全部
        </button>
        <button
          type="button"
          class="filter-btn"
          :class="{ active: tagCategoryFilter === 'organ' }"
          @click="tagCategoryFilter = 'organ'"
        >
          五脏类
        </button>
        <button
          type="button"
          class="filter-btn"
          :class="{ active: tagCategoryFilter === 'chronic' }"
          @click="tagCategoryFilter = 'chronic'"
        >
          慢病类
        </button>
        <button
          type="button"
          class="filter-btn"
          :class="{ active: tagCategoryFilter === 'scale' }"
          @click="tagCategoryFilter = 'scale'"
        >
          量表类
        </button>
      </div>

      <div class="tag-management-body">
        <div class="tag-manual-entry">
          <div class="entry-title">手工新增标签</div>
          <div class="entry-row">
            <select v-model="manualTagCategory" class="entry-select">
              <option value="organ">五脏类</option>
              <option value="chronic">慢病类</option>
              <option value="scale">量表类</option>
            </select>
            <input
              v-model="manualTagInput"
              class="entry-input"
              placeholder="例如：脾功能偏颇、压疮风险低"
            />
            <button type="button" class="entry-btn" @click="handleAddManualTag">添加</button>
          </div>
        </div>

        <div class="tag-result-list">
          <article v-for="tag in filteredTags" :key="tag.id" class="tag-result-item">
            <div class="tag-result-main">
              <span class="tag-item" :class="tag.level">{{ tag.label }}</span>
              <span class="tag-meta">{{ categoryLegend[tag.category] }}</span>
              <span class="tag-meta">{{ tag.source === 'auto' ? '自动匹配' : '手工添加' }}</span>
            </div>
            <button
              type="button"
              class="tag-delete-btn"
              :disabled="tag.source === 'auto'"
              @click="handleDeleteTag(tag)"
            >
              删除
            </button>
          </article>
          <p v-if="filteredTags.length === 0" class="tag-empty">当前分类暂无标签</p>
        </div>
      </div>
    </ElDialog>

    <ElDialog
      v-model="recommendationDialogVisible"
      title="药膳与护理路径推荐"
      width="980px"
      destroy-on-close
      align-center
    >
      <div class="recommendation-profile">
        <span>老人：{{ reportMeta.elderName }}</span>
        <span>照护类型：{{ reportMeta.careType }}</span>
        <span>标签数：{{ mergedTags.length }}</span>
        <span>量表得分：{{ reportMeta.scaleScore }}</span>
      </div>

      <div class="care-level-switch">
        <button
          type="button"
          class="care-level-btn"
          :class="{ active: activeCareLevel === 'fullSelfCare' }"
          @click="activeCareLevel = 'fullSelfCare'"
        >
          全自理
        </button>
        <button
          type="button"
          class="care-level-btn"
          :class="{ active: activeCareLevel === 'semiDisabled' }"
          @click="activeCareLevel = 'semiDisabled'"
        >
          半失能
        </button>
        <button
          type="button"
          class="care-level-btn"
          :class="{ active: activeCareLevel === 'fullyDisabled' }"
          @click="activeCareLevel = 'fullyDisabled'"
        >
          全失能
        </button>
      </div>

      <p class="care-level-summary">
        当前侧重点（{{ careLevelLabelMap[activeCareLevel] }}）：
        {{ careLevelSummaryMap[activeCareLevel] }}
      </p>

      <div class="recommendation-grid">
        <section class="recommendation-card">
          <div class="recommendation-card-header">
            <h3>药膳方案（可按食材储备调整）</h3>
            <ElButton size="small" type="primary" plain @click="handleAddMealPlan">
              新增药膳
            </ElButton>
          </div>

          <article
            v-for="item in mealPlanDraftMap[activeCareLevel]"
            :key="item.id"
            class="recommendation-item"
          >
            <div class="recommendation-item-grid">
              <label>
                <span>方案名称</span>
                <input
                  v-model="item.name"
                  class="recommendation-input"
                  placeholder="请输入药膳名称"
                />
              </label>
              <label>
                <span>食材/配伍</span>
                <input
                  v-model="item.ingredients"
                  class="recommendation-input"
                  placeholder="请输入食材，可按库存调整"
                />
              </label>
              <label class="full-row">
                <span>推荐依据</span>
                <textarea
                  v-model="item.focus"
                  class="recommendation-textarea"
                  placeholder="请输入针对该照护等级的推荐依据"
                ></textarea>
              </label>
            </div>
            <ElButton size="small" type="danger" plain @click="handleRemoveMealPlan(item.id)">
              删除
            </ElButton>
          </article>
        </section>

        <section class="recommendation-card">
          <div class="recommendation-card-header">
            <h3>护理路径（可按照护条件调整）</h3>
            <ElButton size="small" type="primary" plain @click="handleAddNursingPath">
              新增步骤
            </ElButton>
          </div>

          <article
            v-for="item in nursingPathDraftMap[activeCareLevel]"
            :key="item.id"
            class="recommendation-item"
          >
            <div class="recommendation-item-grid">
              <label>
                <span>步骤名称</span>
                <input
                  v-model="item.stepName"
                  class="recommendation-input"
                  placeholder="请输入护理步骤名称"
                />
              </label>
              <label>
                <span>执行频次</span>
                <input
                  v-model="item.frequency"
                  class="recommendation-input"
                  placeholder="如：每日 2 次"
                />
              </label>
              <label class="full-row">
                <span>护理要点</span>
                <textarea
                  v-model="item.focus"
                  class="recommendation-textarea"
                  placeholder="请输入护理要点"
                ></textarea>
              </label>
            </div>
            <ElButton size="small" type="danger" plain @click="handleRemoveNursingPath(item.id)">
              删除
            </ElButton>
          </article>
        </section>
      </div>

      <template #footer>
        <div class="recommendation-footer">
          <ElButton @click="handleResetCurrentLevelRecommendation">恢复当前等级默认推荐</ElButton>
          <ElButton type="primary" @click="recommendationDialogVisible = false">完成调整</ElButton>
        </div>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.report-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 16px;
  color: #1f2937;
}

.report-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.report-title {
  margin: 0;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 600;
}

.report-subtitle {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.meta-wrap {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px 12px;
  color: #334155;
  font-size: 13px;
  align-items: center;
}

.manage-tag-btn {
  margin-left: 8px;
}

.legend {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.legend-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #fff;
}

.portrait-layout {
  display: grid;
  grid-template-columns: minmax(150px, 1fr) minmax(320px, 420px) minmax(150px, 1fr);
  gap: 12px;
  align-items: stretch;
}

.overview-layout {
  display: grid;
  grid-template-columns: 60% minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  margin-bottom: 12px;
}

.overview-left,
.overview-right {
  min-width: 0;
}

.overview-right {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.system-column {
  display: grid;
  grid-template-rows: repeat(4, minmax(76px, auto));
  gap: 10px;
}

.system-card {
  border-radius: 12px;
  padding: 10px 12px;
  color: #0f172a;
  border: 1px solid #e2e8f0;
}

.system-card h3 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}

.system-card p {
  margin: 0;
  font-size: 13px;
  color: #334155;
}

.body-panel {
  border-radius: 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  padding: 12px;
}

.body-outline {
  position: relative;
  height: 520px;
  background: linear-gradient(180deg, #f8fbff 0%, #f5f8fd 100%);
  border-radius: 12px;
  overflow: hidden;
}

.head,
.neck,
.shoulder,
.torso,
.pelvis,
.arm,
.hand,
.leg,
.foot {
  position: absolute;
  background: #dbe6f1;
  border: 1px solid #bed0e0;
}

.head {
  width: 62px;
  height: 62px;
  border-radius: 50%;
  top: 26px;
  left: 50%;
  transform: translateX(-50%);
}

.neck {
  width: 24px;
  height: 20px;
  border-radius: 10px;
  top: 86px;
  left: 50%;
  transform: translateX(-50%);
}

.shoulder {
  width: 146px;
  height: 28px;
  border-radius: 14px;
  top: 102px;
  left: 50%;
  transform: translateX(-50%);
}

.torso {
  width: 88px;
  height: 174px;
  border-radius: 34px;
  top: 122px;
  left: 50%;
  transform: translateX(-50%);
}

.pelvis {
  width: 104px;
  height: 36px;
  border-radius: 18px;
  top: 286px;
  left: 50%;
  transform: translateX(-50%);
}

.arm.upper {
  width: 28px;
  height: 98px;
  border-radius: 16px;
  top: 124px;
}

.arm.upper.left {
  left: 126px;
  transform: rotate(6deg);
}

.arm.upper.right {
  right: 126px;
  transform: rotate(-6deg);
}

.arm.lower {
  width: 24px;
  height: 88px;
  border-radius: 14px;
  top: 214px;
}

.arm.lower.left {
  left: 116px;
  transform: rotate(6deg);
}

.arm.lower.right {
  right: 116px;
  transform: rotate(-6deg);
}

.hand {
  width: 20px;
  height: 34px;
  border-radius: 12px;
  top: 300px;
}

.hand.left {
  left: 112px;
}

.hand.right {
  right: 112px;
}

.leg.thigh {
  width: 30px;
  height: 108px;
  border-radius: 16px;
  top: 324px;
}

.leg.thigh.left {
  left: 42%;
}

.leg.thigh.right {
  right: 42%;
}

.leg.calf {
  width: 26px;
  height: 78px;
  border-radius: 14px;
  top: 432px;
}

.leg.calf.left {
  left: 40%;
}

.leg.calf.right {
  right: 40%;
}

.foot {
  width: 44px;
  height: 14px;
  border-radius: 7px;
  top: 504px;
}

.foot.left {
  left: 34%;
}

.foot.right {
  right: 34%;
}

.body-label {
  position: absolute;
  font-size: 11px;
  color: #64748b;
  padding: 1px 6px;
  border-radius: 999px;
  background: #f1f5f9;
  border: 1px solid #dbe6f1;
}

.body-label.left-arm {
  left: 58px;
  top: 230px;
}

.body-label.right-arm {
  right: 58px;
  top: 230px;
}

.body-label.left-leg {
  left: 98px;
  top: 468px;
}

.body-label.right-leg {
  right: 98px;
  top: 468px;
}

.body-label.trunk {
  left: 50%;
  transform: translateX(-50%);
  top: 180px;
}

.marker {
  position: absolute;
  transform: translate(-50%, -50%);
  border-radius: 10px;
  padding: 4px 8px;
  border: 1px solid transparent;
  min-width: 70px;
}

.marker .name {
  display: block;
  font-size: 12px;
  font-weight: 600;
}

.marker .value {
  display: block;
  font-size: 11px;
  line-height: 1.2;
}

.marker .symbol {
  position: absolute;
  top: -6px;
  right: -8px;
  font-style: normal;
  font-size: 13px;
  font-weight: 700;
}

.monitor {
  border-radius: 16px;
}

.report-content {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.mobile-preview-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mobile-preview-title {
  margin: 0;
  font-size: 14px;
  color: #334155;
}

.phone-shell {
  border-radius: 28px;
  border: 2px solid #1e293b;
  background: #0f172a;
  padding: 10px 8px 12px;
  width: 100%;
  max-width: 320px;
  align-self: flex-start;
}

.phone-notch {
  width: 110px;
  height: 12px;
  border-radius: 999px;
  background: #334155;
  margin: 0 auto 8px;
}

.phone-screen {
  border-radius: 20px;
  background: #f8fafc;
  min-height: 468px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
}

.app-profile-card {
  border-radius: 10px;
  border: 1px solid #dbe6f1;
  background: #fff;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.profile-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #334155;
}

.profile-line strong {
  color: #0f172a;
}

.app-section {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  padding: 8px;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 6px;
}

.app-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.app-tag {
  border-radius: 999px;
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  font-size: 11px;
  color: #1d4ed8;
  padding: 1px 6px;
}

.app-item {
  font-size: 12px;
  color: #334155;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.app-item small {
  color: #64748b;
  font-size: 11px;
}

.app-bottom-bar {
  margin-top: auto;
  border-top: 1px solid #dbe6f1;
  padding-top: 8px;
  display: flex;
  justify-content: space-around;
  font-size: 11px;
  color: #64748b;
}

.app-bottom-bar .active {
  color: #2563eb;
  font-weight: 600;
}

@media (max-width: 1320px) {
  .overview-layout {
    grid-template-columns: 1fr;
  }

  .overview-right .phone-shell {
    max-width: 290px;
  }
}

.mobile-side-note {
  display: grid;
  gap: 8px;
}

.system-card.compact h4 {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
}

.content-card {
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  padding: 10px;
}

.content-card h3 {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
}

.content-card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: #334155;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-item {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid transparent;
}

.report-footer {
  margin-top: 10px;
  font-size: 13px;
  color: #475569;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
}

.tag-management-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-label {
  font-size: 13px;
  color: #475569;
}

.filter-btn {
  border: 1px solid #cbd5e1;
  background: #f8fafc;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
  color: #334155;
  cursor: pointer;
}

.filter-btn.active {
  border-color: #3b82f6;
  color: #1d4ed8;
  background: #eff6ff;
}

.tag-management-body {
  margin-top: 10px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

.entry-title {
  font-size: 13px;
  color: #475569;
  margin-bottom: 6px;
}

.entry-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.entry-select,
.entry-input {
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  min-height: 32px;
  padding: 0 8px;
  font-size: 13px;
  color: #1f2937;
}

.entry-select {
  width: 112px;
}

.entry-input {
  flex: 1;
  min-width: 220px;
}

.entry-btn {
  border: none;
  border-radius: 8px;
  min-height: 32px;
  padding: 0 12px;
  font-size: 13px;
  color: #fff;
  background: #2563eb;
  cursor: pointer;
}

.tag-result-list {
  display: grid;
  gap: 8px;
}

.tag-result-item {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.tag-result-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tag-meta {
  font-size: 12px;
  color: #64748b;
}

.tag-delete-btn {
  border: 1px solid #fca5a5;
  border-radius: 8px;
  font-size: 12px;
  color: #b91c1c;
  background: #fff1f2;
  min-height: 28px;
  padding: 0 10px;
  cursor: pointer;
}

.tag-delete-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.tag-empty {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

.normal {
  background: #e7f7ec;
  border-color: #85d39f;
  color: #1c8a47;
}

.mild {
  background: #fff8dd;
  border-color: #efcf63;
  color: #9a7a00;
}

.moderate {
  background: #ffefde;
  border-color: #f5b36c;
  color: #ad5f10;
}

.severe {
  background: #ffe8ea;
  border-color: #f59aa2;
  color: #c13545;
}

.recommendation-profile {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  font-size: 13px;
  color: #475569;
  margin-bottom: 10px;
}

.care-level-switch {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.care-level-btn {
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  background: #f8fafc;
  min-height: 30px;
  padding: 0 12px;
  font-size: 12px;
  color: #334155;
  cursor: pointer;
}

.care-level-btn.active {
  border-color: #2563eb;
  color: #1d4ed8;
  background: #eff6ff;
}

.care-level-summary {
  margin: 0 0 12px;
  font-size: 13px;
  color: #475569;
}

.recommendation-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.recommendation-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 10px;
  background: #f8fbff;
}

.recommendation-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.recommendation-card-header h3 {
  margin: 0;
  font-size: 14px;
  color: #1e293b;
}

.recommendation-item {
  border: 1px solid #dbe6f1;
  border-radius: 10px;
  background: #fff;
  padding: 8px;
  margin-bottom: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.recommendation-item:last-child {
  margin-bottom: 0;
}

.recommendation-item-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.recommendation-item-grid label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #475569;
}

.recommendation-item-grid .full-row {
  grid-column: 1 / -1;
}

.recommendation-input,
.recommendation-textarea {
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font-size: 13px;
  color: #1f2937;
  padding: 6px 8px;
}

.recommendation-textarea {
  min-height: 56px;
  resize: vertical;
}

.recommendation-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
