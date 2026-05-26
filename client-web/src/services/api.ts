import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('im_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export interface LoginResponse {
  code: number
  msg: string
  token: string
  userId: number
  username: string
  nickname: string
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  const res = await api.post('/login', { username, password })
  return res.data
}

export async function register(username: string, password: string): Promise<LoginResponse> {
  const res = await api.post('/register', { username, password })
  return res.data
}

export async function getUserInfo(userId: number) {
  const res = await api.get(`/user/${userId}`)
  return res.data
}

export async function getFriendList(lastUserId = 0, limit = 50) {
  const res = await api.get('/friend/list', { params: { lastUserId, limit } })
  return res.data
}

export async function applyFriend(targetUserId: number, message = '') {
  const res = await api.post('/friend/apply', { targetUserId, message })
  return res.data
}

export async function createGroup(name: string, memberIds: number[]) {
  const res = await api.post('/group/create', { name, memberIds })
  return res.data
}

export async function getGroupMembers(groupId: number) {
  const res = await api.get(`/group/${groupId}/members`)
  return res.data
}
