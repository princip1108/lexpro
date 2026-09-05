<template>
  <div class="login-wrap">
    <div class="login-box">
      <div class="login-head"><div class="big">⚖️</div><h2>法律大模型与检察业务融合应用系统</h2><p>请使用工作账号登录</p></div>
      <div class="login-body">
        <form autocomplete="off" @submit.prevent="submitLogin">
          <div class="field"><label>账号</label><input v-model="form.username" type="text" maxlength="100" placeholder="请输入账号" autocomplete="username"></div>
          <div class="field"><label>密码</label><input v-model="form.password" type="password" maxlength="72" placeholder="请输入密码" autocomplete="current-password"></div>
          <div class="field"><label>验证码</label><div class="captcha-row"><input v-model="form.captcha" type="text" placeholder="请输入验证码" maxlength="4"><button class="captcha-img" type="button" title="点击刷新" aria-label="刷新验证码" :disabled="captchaLoading" @click="refreshCaptcha"><img v-if="captchaSvg" :src="captchaSvg" alt="验证码"><span v-else>{{ captchaLoading ? '加载中…' : '点击刷新' }}</span></button></div></div>
          <button type="submit" class="btn primary lg btn-block" :disabled="submitting"><span v-if="submitting" class="spin"></span>{{ submitting ? '登录中…' : '登 录' }}</button>
          <div class="login-tip">系统不开放注册，请使用管理员分配的工作账号。</div>
          <div v-if="demoAccounts.length" class="login-tip demo-accounts"><b>演示账号（仅用于本地验收）</b><div v-for="account in demoAccounts" :key="account.username"><button type="button" @click="form.username=account.username;form.password=account.password">{{ account.name }}</button>：{{ account.username }} / {{ account.password }}</div></div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, getCaptcha } from '../api/auth'
import { saveSession } from '../auth/session'

const route = useRoute()
const router = useRouter()
const submitting = ref(false)
const captchaId = ref('')
const captchaSvg = ref('')
const captchaLoading = ref(false)
const form = reactive({ username: '', password: '', captcha: '' })
// Public local demonstration credentials, eliminated from production builds.
const demoAccounts = import.meta.env.DEV ? [
  {name:'管理员',username:'demo_admin',password:'LexProDemo2026!'},
  {name:'检察官',username:'demo_prosecutor',password:'LexProDemo2026!'},
  {name:'审查人员',username:'demo_reviewer',password:'LexProDemo2026!'},
] : []

async function refreshCaptcha() {
  if (captchaLoading.value) return
  captchaLoading.value = true
  captchaId.value = ''; captchaSvg.value = ''; form.captcha = ''
  try {
    const challenge = await getCaptcha()
    captchaId.value = challenge.captchaId
    captchaSvg.value = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(challenge.svg)
  } catch (error) { ElMessage.error(error.message || '验证码加载失败') }
  finally { captchaLoading.value = false }
}
onMounted(refreshCaptcha)

async function submitLogin() {
  if (submitting.value) return
  if (!form.username.trim()) return ElMessage.error('请输入账号')
  if (!form.password) return ElMessage.error('请输入密码')
  if (!form.captcha.trim() || !captchaId.value) return ElMessage.error('请输入验证码')
  submitting.value = true
  try {
    const response = await login({ username: form.username.trim(), password: form.password, captchaId: captchaId.value, captcha: form.captcha.trim() })
    saveSession(response, false)
    form.password = ''
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') && !route.query.redirect.startsWith('//') ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch (error) { ElMessage.error(error.message || '登录失败'); refreshCaptcha() }
  finally { submitting.value = false }
}
</script>

<style scoped>
.captcha-img{padding:0;flex-shrink:0}.captcha-img img{width:100%;height:100%;display:block}.btn-block{width:100%;justify-content:center}
.demo-accounts{text-align:left;line-height:1.9;overflow-wrap:anywhere}.demo-accounts button{border:0;background:none;padding:0;color:var(--blue);cursor:pointer;font:inherit}
</style>
