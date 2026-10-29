import { defineStore } from 'pinia'
import { login as loginApi, getProfile as getProfileApi } from '@/api/auth'
import {
  clearAuthStorage,
  getAuthToken,
  getAuthUser,
  setAuthToken,
  setAuthUser
} from '@/utils/authStorage'

/** 各角色登录后的默认首页 */
export const ROLE_HOME = {
  ADMIN: '/admin/users',
  TEACHER: '/teacher/courses',
  STUDENT: '/student/courses'
}

export function homeOfRole(role) {
  return ROLE_HOME[role] || '/login'
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getAuthToken(),
    user: getAuthUser(),
    /** 是否已从后端拉取过档案（用于路由守卫中的一次性补全） */
    profileLoaded: false
  }),

  getters: {
    isLogin: (state) => !!state.token,
    role: (state) => state.user?.role || '',
    roleLabel: (state) => state.user?.roleLabel || '',
    realName: (state) => state.user?.realName || state.user?.username || '',
    isAdmin: (state) => state.user?.role === 'ADMIN',
    isTeacher: (state) => state.user?.role === 'TEACHER',
    isStudent: (state) => state.user?.role === 'STUDENT',
    homePath: (state) => homeOfRole(state.user?.role)
  },

  actions: {
    /** 登录：保存令牌与用户信息 */
    async login(payload) {
      const res = await loginApi(payload)
      const data = res?.data || {}
      this.token = data.token || ''
      this.user = data.user || null
      this.profileLoaded = !!this.user
      setAuthToken(this.token)
      setAuthUser(this.user)
      return data
    },

    /** 拉取当前用户档案，同步 UserVO */
    async fetchProfile() {
      const res = await getProfileApi()
      const data = res?.data || null
      if (data) {
        // /auth/me 返回 ProfileVO，与 UserVO 字段基本一致，做一次归一化
        this.user = {
          ...this.user,
          ...data,
          id: data.userId ?? this.user?.id ?? null,
          roleLabel: data.roleLabel ?? this.user?.roleLabel ?? '',
          status: data.status ?? this.user?.status ?? 1
        }
        setAuthUser(this.user)
      }
      this.profileLoaded = true
      return this.user
    },

    /** 退出登录 */
    logout() {
      this.token = ''
      this.user = null
      this.profileLoaded = false
      clearAuthStorage()
    }
  }
})
