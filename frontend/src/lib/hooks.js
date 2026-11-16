import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Executa uma funcao assincrona e expõe { data, loading, error, reload, setData }.
 * Ignora respostas antigas quando as dependencias mudam.
 */
export function useAsync(fn, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null })
  const run = useRef(0)

  const load = useCallback(() => {
    const id = ++run.current
    setState((s) => ({ ...s, loading: true, error: null }))
    return Promise.resolve()
      .then(fn)
      .then((data) => { if (id === run.current) setState({ data, loading: false, error: null }) })
      .catch((error) => { if (id === run.current) setState({ data: null, loading: false, error }) })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => { load() }, [load])

  const setData = useCallback((updater) => {
    setState((s) => ({ ...s, data: typeof updater === 'function' ? updater(s.data) : updater }))
  }, [])

  return { ...state, reload: load, setData }
}

export function useDocumentTitle(title) {
  useEffect(() => {
    const prev = document.title
    document.title = title ? `${title} | Cadeira Livre` : 'Cadeira Livre'
    return () => { document.title = prev }
  }, [title])
}

export function useDebounced(value, ms = 250) {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}
