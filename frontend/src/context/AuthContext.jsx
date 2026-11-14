import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, STORAGE_KEYS } from '../lib/http'
import { store } from '../lib/storage'

const AuthCtx = createContext(null)

/** Sessao do dono da barbearia (papel DONO no backend). */
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
      // o login e o mesmo para dono e cliente; esta tela e so do painel
      if (s.usuario.papel !== 'DONO') throw new Error('Esta conta é de cliente. Para agendar, use "Sou cliente, quero agendar um horário".')
      persist(s)
      return s.usuario
    },
    async cadastrar(dados) {
      const s = await api.post('/auth/cadastro', dados)
      persist(s)
      return s.usuario
    },
    /** Encerra a sessao no backend (RF05) e limpa a do navegador, mesmo se o servidor nao responder. */
    async logout() {
      try {
        await api.post('/auth/logout', undefined, { as: 'staff' })
      } catch {
        /* sessao ja encerrada ou servidor fora do ar: sai do mesmo jeito */
      }
      persist(null)
    },
    /** So para a barra de prototipo (modo simulado): entra direto como um dos perfis de teste. */
    demoLogin: (papel) => persist(papel === 'administrador'
      ? { token: 'mock.staff.u-admin', usuario: { id: 'u-admin', nome: 'Rafael Lima', papel: 'DONO', barbeariaId: 's1', profissionalId: 'p1' } }
      : { token: 'mock.staff.u-func', usuario: { id: 'u-func', nome: 'Rafael Lima', papel, salaoId: 's1', profissionalId: 'p1' } }),
  }), [session, persist])

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>
}

export const useAuth = () => useContext(AuthCtx)

/** O dono vai para o painel. A agenda do funcionario so existe na barra do prototipo. */
export const homeDoPapel = (usuario) => (usuario?.papel === 'DONO' ? '/painel' : '/agenda')
