import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, STORAGE_KEYS } from '../lib/http'
import { store } from '../lib/storage'

const AuthCtx = createContext(null)

/** Sessao do responsavel / funcionario. */
export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => store.get(STORAGE_KEYS.staff))

  const persist = useCallback((s) => {
    setSession(s)
    if (s) store.set(STORAGE_KEYS.staff, s)
    else store.remove(STORAGE_KEYS.staff)
  }, [])

  useEffect(() => {
    const onExpire = (e) => { if (e.detail?.as === 'staff') persist(null) }
    window.addEventListener('cl:unauthorized', onExpire)
    return () => window.removeEventListener('cl:unauthorized', onExpire)
  }, [persist])

  const value = useMemo(() => ({
    usuario: session?.usuario || null,
    async login(dados) {
      const s = await api.post('/auth/login', dados)
      persist(s)
      return s.usuario
    },
    async cadastrar(dados) {
      const s = await api.post('/auth/cadastro', dados)
      persist(s)
      return s.usuario
    },
    logout: () => persist(null),
    /** So para a barra de prototipo (modo simulado): entra direto como um dos perfis de teste. */
    demoLogin: (papel) => persist(papel === 'administrador'
      ? { token: 'mock.staff.u-admin', usuario: { id: 'u-admin', nome: 'Rafael Lima', papel, salaoId: 's1', profissionalId: 'p1' } }
      : { token: 'mock.staff.u-func', usuario: { id: 'u-func', nome: 'Rafael Lima', papel, salaoId: 's1', profissionalId: 'p1' } }),
  }), [session, persist])

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>
}

export const useAuth = () => useContext(AuthCtx)

export const homeDoPapel = (usuario) => (usuario?.papel === 'administrador' ? '/painel' : '/agenda')
