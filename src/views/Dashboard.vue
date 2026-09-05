<template>
  <div v-loading="loading">
    <div class="welcome-banner">
      <div>
        <h2>{{ greeting }},{{ currentUser?.realName || currentUser?.username || '用户' }}</h2>
        <p>当前模型模式:<span class="wb-dot">●</span> <b>{{ modelConfiguration.activeMode || '内置规则引擎(离线)' }}</b> · 当前数据范围:{{ dataScope }}</p>
      </div>
      <div class="flex">
        <button class="btn gold" type="button" @click="router.push('/case-cards')">案卡回填</button>
        <button class="btn navy" type="button" @click="router.push('/review-report')">生成审查报告</button>
      </div>
    </div>

    <div class="grid g4">
      <div v-for="item in cards" :key="item.label" class="stat-card">
        <div class="stat-icon" :class="item.color"><LegalIcon :name="item.icon" /></div>
        <div><div class="v">{{ item.value }}</div><div class="k">{{ item.label }}</div></div>
      </div>
    </div>

    <div class="card mt16">
      <div class="card-title"><span class="bar"></span>快捷入口</div>
      <div class="card-pad">
        <div class="quick-grid">
          <button v-for="item in quickEntries" :key="item.title" class="quick-btn" type="button" @click="router.push(item.to)">
            <div class="qb-i">{{ item.icon }}</div><div class="qb-t">{{ item.title }}</div><div class="qb-s">{{ item.subtitle }}</div>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import LegalIcon from '../components/common/LegalIcon.vue'
import { currentUser, hasPermission } from '../auth/session'
import { getModelConfiguration } from '../api/system'
import { getDashboard } from '../api/workspace'

const router = useRouter()
const loading = ref(false)
const dashboard = ref({ cases: { total: 0, active: 0, pending: 0, overdue: 0 }, tasks: { active: 0, dueToday: 0, overdue: 0, waitingConfirmation: 0 } })
const modelConfiguration = ref({ activeMode: '内置规则引擎(离线)' })
const greeting = computed(() => { const hour = new Date().getHours(); return hour < 6 ? '凌晨好' : hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好' })
const dataScope = computed(() => currentUser.value?.caseScope || '本人承办或已授权案件')
const cards = computed(() => [
  { label: '案件总数', value: dashboard.value.cases.total || 0, icon: 'case', color: 's1' },
  { label: '审查中', value: dashboard.value.cases.reviewing ?? dashboard.value.cases.active ?? 0, icon: 'case', color: 's2' },
  { label: '已结案', value: dashboard.value.cases.closed || 0, icon: 'case', color: 's3' },
  { label: '卷宗文件', value: dashboard.value.results?.dossierTotal || 0, icon: 'report', color: 's4' },
  { label: '实体识别结果', value: dashboard.value.results?.entityResults || 0, icon: 'model', color: 's1' },
  { label: '要素识别结果', value: dashboard.value.results?.elementResults || 0, icon: 'model', color: 's5' },
  { label: '摘要生成结果', value: dashboard.value.results?.summaryResults || 0, icon: 'model', color: 's6' },
  { label: '审查报告', value: dashboard.value.results?.reportTotal || 0, icon: 'report', color: 's2' }
])
const quickEntries = computed(() => [
  { icon: '📁', title: '案件管理', subtitle: '卷宗录入与解析', to: '/todo-cases' },
  { icon: '📚', title: '典型案例库', subtitle: '检索参考案例', to: { path: '/case-recommend', query: { view: 'library' } } },
  { icon: '🗂️', title: '案卡回填', subtitle: '字段自动填充', to: '/case-cards' },
  { icon: '📄', title: '审查报告', subtitle: '报告一键生成', to: '/review-report' },
  { icon: '⚙️', title: '模型配置', subtitle: '大模型接口切换', to: '/model-config' },
  ...(hasPermission('USER_MANAGE') ? [{ icon: '👥', title: '用户管理', subtitle: '账号与权限', to: '/users' }] : [])
])

onMounted(async () => {
  loading.value = true
  try {
    const [dashboardResult, modelResult] = await Promise.allSettled([getDashboard(), getModelConfiguration()])
    if (dashboardResult.status === 'rejected') throw dashboardResult.reason
    dashboard.value = dashboardResult.value
    if (modelResult.status === 'fulfilled') modelConfiguration.value = modelResult.value
  }
  catch (error) { ElMessage.error(error.message) }
  finally { loading.value = false }
})
</script>
