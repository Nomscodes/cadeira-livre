import { api } from '../../lib/http'
import { useToast } from '../../context/ToastContext'
import Toggle from '../../components/ui/Toggle'

/**
 * SOMENTE TESTE. Este arquivo inteiro nao vai para o deploy (VITE_MODO_TESTE=false).
 * Faz aqui o que o administrador faz em Configuracoes: liberar ou bloquear a edicao de horarios pelo funcionario.
 * No sistema final essa permissao e so do administrador, e o servidor responde 403 a quem editar sem ela.
 */
export default function AtalhoTeste({ podeEditar, onAlterado }) {
  const toast = useToast()
  const alternar = async (permitido) => {
    try {
      await api.put('/teste/edicao-horarios', { permitido }, { as: 'staff' })
      onAlterado()
    } catch (e) { toast.error(e.message) }
  }
  return (
    <div className="agenda-controls test-only">
      <span className="badge-teste">somente teste</span>
      <label className="field-label" htmlFor="liberar-edicao" style={{ margin: 0 }}>liberar a edição dos meus horários</label>
      <Toggle id="liberar-edicao" checked={podeEditar} onChange={alternar} />
      <span className="hint">Atalho só para testes. No sistema final esta opção não existe nesta tela: apenas o administrador libera ou bloqueia a edição, em Configurações.</span>
    </div>
  )
}
