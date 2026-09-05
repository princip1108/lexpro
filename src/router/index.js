import { createRouter, createWebHashHistory } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import Home from '../views/Home.vue'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import ReviewReport from '../views/ReviewReport.vue'
import LegalCases from '../views/LegalCases.vue'
import LegalElements from '../views/LegalElements.vue'
import LegalDocumentEntities from '../views/LegalDocumentEntities.vue'
import Summary from '../views/Summary.vue'
import LegalRecommendations from '../views/LegalRecommendations.vue'
import Organization from '../views/Organization.vue'
import CaseCards from '../views/CaseCards.vue'
import ModelConfiguration from '../views/ModelConfiguration.vue'
import OperationLogs from '../views/OperationLogs.vue'
import { hasPermission, hasValidSession } from '../auth/session'

const routes = [
  { path: '/', component: Home, meta: { title: '首页', public: true, guestOnly: true } },
  { path: '/login', component: Login, meta: { title: '登录', public: true } },
  {
    path: '/',
    component: AppLayout,
    children: [
      { path: 'dashboard', component: Dashboard, meta: { title: '工作台', permission: 'DASHBOARD_VIEW' } },
      { path: 'review-report', component: ReviewReport, meta: { title: '审查报告', permission: 'CASE_READ' } },
      { path: 'case-cards', component: CaseCards, meta: { title: '案卡管理', permission: 'CASE_READ' } },
      { path: 'todo-cases', component: LegalCases, meta: { title: '案件管理', permission: 'CASE_READ' } },
      { path: 'legal-elements', component: LegalElements, meta: { title: '法律要素识别', permission: 'CASE_READ' } },
      { path: 'document-entities', component: LegalDocumentEntities, meta: { title: '询问讯问笔录实体识别', permission: 'CASE_READ' } },
      { path: 'summary', component: Summary, meta: { title: '案件摘要', permission: 'CASE_READ' } },
      { path: 'case-recommend', component: LegalRecommendations, meta: { title: '典型案例推送', permission: 'RECOMMENDATION_USE' } },
      { path: 'model-config', component: ModelConfiguration, meta: { title: '模型配置' } },
      { path: 'operation-logs', component: OperationLogs, meta: { title: '操作日志', permission: 'USER_MANAGE' } },
      {
        path: 'users',
        component: Organization,
        meta: { title: '用户管理', permission: 'USER_MANAGE' }
      },
      { path: 'organization', redirect: '/users' }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const authenticated = hasValidSession()
  if (to.meta.public) {
    return authenticated && to.meta.guestOnly ? '/dashboard' : true
  }
  if (!authenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.permission && !hasPermission(to.meta.permission)) {
    return '/dashboard'
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - LexPro` : 'LexPro'
})

export default router
