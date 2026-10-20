import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, STORAGE_KEYS } from '../lib/http'
import { store } from '../lib/storage'

const ClienteCtx = createContext(null)

/** Acesso temporario do cliente final (codigo enviado por WhatsApp ou e-mail, sem senha). */
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
    contato: session?.contato || null,
    enviarCodigo: (canal, contato) => api.post('/acesso/codigo', { canal, contato }),
    async confirmar(contato, codigo) {
      const s = await api.post('/acesso/confirmar', { contato, codigo })
      persist(s)
    },
    sair: () => persist(null),
    /** So para a barra de prototipo (modo simulado): entra direto com o cliente de demonstracao. */
    demoEntrar: () => persist({ token: 'mock.cliente.62955550000', contato: '62955550000' }),
  }), [session, persist])

  return <ClienteCtx.Provider value={value}>{children}</ClienteCtx.Provider>
}

export const useCliente = () => useContext(ClienteCtx)
