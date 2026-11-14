import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, STORAGE_KEYS } from '../lib/http'
import { store } from '../lib/storage'

const ClienteCtx = createContext(null)

/** Sessao do cliente final (papel CLIENTE no backend): conta com e-mail e senha. */
export function ClienteProvider({ children }) {
  const [session, setSession] = useState(() => store.get(STORAGE_KEYS.cliente))

  const persist = useCallback((s) => {
    setSession(s)
    if (s) store.set(STORAGE_KEYS.cliente, s)
    else store.remove(STORAGE_KEYS.cliente)
  }, [])

  useEffect(() => {
    const onExpire = (e) => { if (e.detail?.as === 'cliente') persist(null) }
    window.addEventListener('cl:unauthorized', onExpire)
    return () => window.removeEventListener('cl:unauthorized', onExpire)
  }, [persist])

  const value = useMemo(() => ({
    autenticado: !!session,
    usuario: session?.usuario || null,
    async entrar(dados) {
      const s = await api.post('/auth/login', dados)
      // o login e o mesmo para dono e cliente; esta tela e so do cliente
      if (s.usuario.papel !== 'CLIENTE') throw new Error('Esta conta é de dono de barbearia. Entre pela tela de login do painel.')
      persist(s)
    },
    async cadastrar(dados) {
      const s = await api.post('/auth/cadastro/cliente', dados)
      persist(s)
    },
    /** Encerra a sessao no backend (RF05) e limpa a do navegador, mesmo se o servidor nao responder. */
    async sair() {
      try {
        await api.post('/auth/logout', undefined, { as: 'cliente' })
      } catch {
        /* sessao ja encerrada ou servidor fora do ar: sai do mesmo jeito */
      }
      persist(null)
    },
    /** So para a barra de prototipo (modo simulado): entra direto com o cliente de demonstracao. */
    demoEntrar: () => persist({ token: 'mock.cliente.62955550000', usuario: { id: 'c1', nome: 'Gabriel Naoki', papel: 'CLIENTE', barbeariaId: null } }),
  }), [session, persist])

  return <ClienteCtx.Provider value={value}>{children}</ClienteCtx.Provider>
}

export const useCliente = () => useContext(ClienteCtx)
