import { beforeEach, describe, expect, it } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from '../App'
import { resetMock } from '../mocks/db'

const abrir = (url) => {
  window.history.pushState({}, '', url)
  return render(<App />)
}
const espera = { timeout: 5000 }

beforeEach(() => {
  resetMock()
  window.history.pushState({}, '', '/')
})

describe('tema do salao', () => {
  it('aplica a cor do salao so no portal e remove ao sair', async () => {
    const view = abrir('/barbearia-do-rafael')
    expect(await screen.findByRole('heading', { name: 'Barbearia do Rafael', level: 1 }, espera)).toBeInTheDocument()
    expect(document.documentElement.style.getPropertyValue('--c-primary')).toBe('#1B4F9C') // azul escuro
    view.unmount()
    expect(document.documentElement.style.getPropertyValue('--c-primary')).toBe('')
  })
  it('usa outra cor em outro salao (pastel violeta com texto escuro)', async () => {
    abrir('/studio-juliana-matos')
    await screen.findByRole('heading', { name: 'Studio Juliana Matos', level: 1 }, espera)
    expect(document.documentElement.style.getPropertyValue('--c-primary')).toBe('#D9B4EC')
    expect(document.documentElement.style.getPropertyValue('--c-on-primary')).toBe('#2A1F22')
  })
  it('paginas da plataforma nao recebem tema do salao', async () => {
    abrir('/login')
    await screen.findByRole('tab', { name: /entrar/i })
    expect(document.documentElement.style.getPropertyValue('--c-primary')).toBe('')
  })
  it('portal oculto e inexistente tem mensagens proprias', async () => {
    const a = abrir('/espaco-aurora')
    expect(await screen.findByText('Este salão vai voltar em breve', {}, espera)).toBeInTheDocument()
    a.unmount()
    abrir('/nao-existe-mesmo')
    expect(await screen.findByText('Esta página não existe', {}, espera)).toBeInTheDocument()
  })
})

describe('login', () => {
  it('administrador entra e ve o painel; funcionario cai na agenda', async () => {
    const user = userEvent.setup()
    const v = abrir('/login')
    expect(await screen.findByLabelText('e-mail')).toBeInTheDocument()
    expect(screen.queryByLabelText('Administrador')).not.toBeInTheDocument() // o cargo e reconhecido pelo login
    expect(screen.queryByLabelText('Funcionário')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('e-mail'), 'admin')
    await user.type(screen.getByLabelText('senha'), 'admin')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('heading', { name: 'Meu portal' }, espera)).toBeInTheDocument()
    expect(window.location.pathname).toBe('/painel')
    v.unmount()
    window.localStorage.clear()

    abrir('/login')
    await user.type(await screen.findByLabelText('e-mail'), 'func')
    await user.type(screen.getByLabelText('senha'), 'func')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('heading', { name: /Minha agenda/, level: 1 }, espera)).toBeInTheDocument()
    expect(window.location.pathname).toBe('/agenda')
  })
  it('mostra erro com senha errada', async () => {
    const user = userEvent.setup()
    abrir('/login')
    await user.type(await screen.findByLabelText('e-mail'), 'admin')
    await user.type(screen.getByLabelText('senha'), 'errada')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('alert', {}, espera)).toHaveTextContent(/incorretos/)
  })
  it('cadastro valida campos antes de enviar', async () => {
    const user = userEvent.setup()
    abrir('/login?aba=cadastro')
    await user.click(await screen.findByRole('button', { name: 'Criar conta' }))
    expect(await screen.findByText('Informe nome e sobrenome.')).toBeInTheDocument()
    expect(screen.getByText('Aceite os termos para criar a conta.')).toBeInTheDocument()
  })
  it('rotas protegidas redirecionam para o login', async () => {
    abrir('/painel')
    expect(await screen.findByRole('tab', { name: /entrar/i }, espera)).toBeInTheDocument()
    expect(window.location.pathname).toBe('/login')
  })
})

describe('acesso do cliente e busca', () => {
  it('codigo errado e certo, depois lista saloes', async () => {
    const user = userEvent.setup()
    abrir('/buscar')
    expect(await screen.findByRole('heading', { name: 'Acesso temporário' }, espera)).toBeInTheDocument()
    await user.type(screen.getByLabelText('seu contato'), '62999990000')
    await user.click(screen.getByRole('button', { name: 'Enviar código' }))
    await user.type(await screen.findByLabelText('código recebido', {}, espera), '000000')
    await user.click(screen.getByRole('button', { name: 'Confirmar código' }))
    expect(await screen.findByText(/Código incorreto/, {}, espera)).toBeInTheDocument()
    await user.clear(screen.getByLabelText('código recebido'))
    await user.type(screen.getByLabelText('código recebido'), '123456')
    await user.click(screen.getByRole('button', { name: 'Confirmar código' }))
    expect(await screen.findByRole('heading', { name: 'Encontre seu salão' }, espera)).toBeInTheDocument()
    expect(await screen.findByText('Barbearia do Rafael', {}, espera)).toBeInTheDocument()
    expect(screen.getByText('Studio Juliana Matos')).toBeInTheDocument()
    expect(screen.queryByText('Diego Santos Barber')).not.toBeInTheDocument() // outra cidade
  })
})

describe('agendamento completo', () => {
  it('escolhe servico, data, horario e confirma', async () => {
    const user = userEvent.setup()
    abrir('/barbearia-do-rafael/agendar?servico=sv2')
    // Barba so tem o Rafael: profissional atribuido sozinho, resumo liberado
    expect(await screen.findByText('tempo médio total', {}, espera)).toBeInTheDocument()
    expect(screen.getByText('30 min')).toBeInTheDocument()

    const cal = await screen.findByRole('group', { name: 'Escolha o dia' })
    let dia
    await waitFor(() => {
      dia = within(cal).getAllByRole('button').filter((b) => b.getAttribute('aria-label')?.match(/^\d+ de \w+$/) && !b.disabled)
      expect(dia.length).toBeGreaterThan(0)
    }, espera)
    // evita o dia de hoje (horarios podem ja ter passado)
    const alvo = dia.find((b) => !b.classList.contains('today')) || dia[0]
    await user.click(alvo)
    const slot = await waitFor(() => {
      const s = screen.getAllByRole('button').filter((b) => /^\d\d:\d\d$/.test(b.textContent) && !b.disabled)
      expect(s.length).toBeGreaterThan(0)
      return s[0]
    }, espera)
    await user.click(slot)

    await user.click(screen.getByRole('button', { name: 'Confirmar informações' }))
    const dialogo = await screen.findByRole('dialog')
    expect(within(dialogo).getByText(/4 horas/)).toBeInTheDocument()
    const confirmar = within(dialogo).getByRole('button', { name: 'Confirmar' })
    expect(confirmar).toBeDisabled()
    await user.type(within(dialogo).getByLabelText(/seu nome completo/), 'Joana Teste')
    await user.type(within(dialogo).getByLabelText(/seu WhatsApp/), '62988887777')
    await user.click(within(dialogo).getByLabelText('todas as informações estão corretas'))
    expect(confirmar).toBeEnabled()
    await user.click(confirmar)
    expect(await screen.findByText('Agendamento enviado', {}, espera)).toBeInTheDocument()
    expect(screen.getByText(/mensagem de confirmação pelo WhatsApp/)).toBeInTheDocument()
  })
  it('exige profissional para servico com mais de uma opcao', async () => {
    const user = userEvent.setup()
    abrir('/barbearia-do-rafael/agendar?servico=sv1') // Corte: Rafael ou Juliana
    expect(await screen.findByText(/Ainda falta escolher um profissional/, {}, espera)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar informações' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: /Juliana/ }))
    expect(await screen.findByText('tempo médio total', {}, espera)).toBeInTheDocument()
  })
})

describe('painel do responsavel', () => {
  const entrarAdmin = async () => {
    window.localStorage.setItem('cl:staff', JSON.stringify({ token: 'mock.staff.u-admin', usuario: { id: 'u-admin', nome: 'Rafael Lima', papel: 'administrador', salaoId: 's1', profissionalId: 'p1' } }))
  }
  it('mostra o seletor com 14 cores e troca a previa', async () => {
    const user = userEvent.setup()
    await entrarAdmin()
    abrir('/painel')
    const grupos = await screen.findAllByRole('radiogroup', {}, espera)
    const cores = screen.getAllByRole('radio').filter((r) => /escuro|pastel/.test(r.getAttribute('aria-label')))
    expect(cores).toHaveLength(14)
    expect(grupos.length).toBeGreaterThanOrEqual(2)
    await user.click(screen.getByRole('radio', { name: 'Verde pastel' }))
    expect(screen.getByRole('radio', { name: 'Verde pastel' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByText(/Cor escolhida:/)).toHaveTextContent('Verde pastel')
    // a cor vale na hora, em toda a tela (previa)
    expect(document.documentElement.style.getPropertyValue('--c-primary')).toBe('#B5E0BD')
    expect(screen.getByText(/ela só é guardada ao clicar em/)).toBeInTheDocument()
  })
  it('o painel usa a cor salva do salao e a previa some se sair sem salvar', async () => {
    const user = userEvent.setup()
    await entrarAdmin()
    abrir('/painel')
    await screen.findByRole('radio', { name: 'Laranja escuro' }, espera)
    expect(document.documentElement.style.getPropertyValue('--c-primary')).toBe('#1B4F9C') // azul escuro salvo
    await user.click(screen.getByRole('radio', { name: 'Laranja escuro' }))
    expect(document.documentElement.style.getPropertyValue('--c-primary')).toBe('#B4510F')
    await user.click(screen.getByRole('link', { name: 'agendamentos' }))
    await screen.findByText('Marcos Silva', {}, espera)
    expect(document.documentElement.style.getPropertyValue('--c-primary')).toBe('#1B4F9C') // voltou
  })
  it('salva portal e o novo tema aparece no portal publico', async () => {
    const user = userEvent.setup()
    await entrarAdmin()
    const v = abrir('/painel')
    await user.click(await screen.findByRole('radio', { name: 'Laranja escuro' }, espera))
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))
    expect(await screen.findByText(/Alterações salvas/, {}, espera)).toBeInTheDocument()
    v.unmount()
    abrir('/barbearia-do-rafael')
    await screen.findByRole('heading', { name: 'Barbearia do Rafael', level: 1 }, espera)
    expect(document.documentElement.style.getPropertyValue('--c-primary')).toBe('#B4510F')
  })
  it.each([
    ['/painel/agendamentos', 'Marcos Silva'],
    ['/painel/servicos', 'Combos de serviços'],
    ['/painel/profissionais', 'Equipe'],
    ['/painel/configuracoes', 'Salvar configurações'],
  ])('renderiza %s', async (rota, texto) => {
    await entrarAdmin()
    abrir(rota)
    expect(await screen.findByText(texto, {}, espera)).toBeInTheDocument()
  })
  it('cadastra servico novo pelo modal e ele aparece', async () => {
    const user = userEvent.setup()
    await entrarAdmin()
    abrir('/painel/servicos')
    await user.click(await screen.findByRole('button', { name: /novo serviço/ }, espera))
    const d = await screen.findByRole('dialog')
    await user.type(within(d).getByLabelText(/nome do serviço/), 'Hidratação')
    await user.type(within(d).getByLabelText(/valor \(R\$\)/), '55,50')
    await user.type(within(d).getByLabelText(/tempo médio/), '45')
    await user.click(within(d).getByRole('button', { name: 'Salvar' }))
    expect(await screen.findByText('Hidratação', {}, espera)).toBeInTheDocument()
    expect(screen.getByText(/R\$ 55,50/)).toBeInTheDocument()
  })
  it('modal de servico valida campos', async () => {
    const user = userEvent.setup()
    await entrarAdmin()
    abrir('/painel/servicos')
    await user.click(await screen.findByRole('button', { name: /novo serviço/ }, espera))
    const d = await screen.findByRole('dialog')
    await user.click(within(d).getByRole('button', { name: 'Salvar' }))
    expect(await within(d).findByText('Informe o nome do serviço.')).toBeInTheDocument()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
  it('config nao deixa publicar portal sem servico vinculado e confirma apagar dados com palavra', async () => {
    const user = userEvent.setup()
    await entrarAdmin()
    abrir('/painel/configuracoes')
    await user.click(await screen.findByRole('button', { name: 'Apagar todos os dados' }, espera))
    const d = await screen.findByRole('dialog')
    const botao = within(d).getByRole('button', { name: 'Apagar dados' })
    expect(botao).toBeDisabled()
    await user.type(within(d).getByLabelText(/digite APAGAR/), 'apagar')
    expect(botao).toBeEnabled()
  })
})

describe('tela do funcionario', () => {
  const entrarFunc = () => window.localStorage.setItem('cl:staff', JSON.stringify({ token: 'mock.staff.u-func', usuario: { id: 'u-func', nome: 'Rafael Lima', papel: 'funcionario', salaoId: 's1', profissionalId: 'p1' } }))
  it('mostra perfil, indicadores e a lista de agendamentos com abas', async () => {
    const user = userEvent.setup()
    entrarFunc()
    abrir('/agenda')
    expect(await screen.findByRole('heading', { name: 'Rafael Lima', level: 2 }, espera)).toBeInTheDocument()
    expect(screen.getByText('Seg a Sáb, 09h às 18h')).toBeInTheDocument()
    expect(screen.getByText('Barba', { selector: '.chip' })).toBeInTheDocument()
    expect(screen.getByText('atendimentos hoje')).toBeInTheDocument()
    expect(document.documentElement.style.getPropertyValue('--c-primary')).toBe('#1B4F9C') // cor do salao

    const tabela = await screen.findByRole('table', {}, espera)
    expect(within(tabela).getByText('Marcos Silva')).toBeInTheDocument()
    expect(within(tabela).getByText('Lucas Ferreira')).toBeInTheDocument()
    expect(within(tabela).queryByText('Pedro Alves')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'realizados (2)' }))
    expect(within(screen.getByRole('table')).getByText('Pedro Alves')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'cancelados (1)' }))
    await user.click(within(screen.getByRole('table')).getByText('Carlos Mendes'))
    const dlg = await screen.findByRole('dialog')
    expect(within(dlg).getByText('Carlos Mendes')).toBeInTheDocument()
    expect(within(dlg).getByRole('button', { name: 'Alertar sobre este cliente' })).toBeInTheDocument()
  })
})

describe('agenda do funcionario', () => {
  it('abre detalhe de agendamento e permite alertar', async () => {
    const user = userEvent.setup()
    window.localStorage.setItem('cl:staff', JSON.stringify({ token: 'mock.staff.u-func', usuario: { id: 'u-func', nome: 'Rafael Lima', papel: 'funcionario', salaoId: 's1', profissionalId: 'p1' } }))
    abrir('/agenda')
    await screen.findByRole('heading', { name: /Minha agenda/, level: 1 }, espera)
    // procura um dia com agendamento: os de teste ficam 2 e 3 dias uteis a frente
    const cal = await screen.findByRole('group', { name: 'Escolha o dia' }, espera)
    const dias = within(cal).getAllByRole('button').filter((b) => !b.disabled)
    expect(dias.length).toBeGreaterThan(5)
    let achou = false
    for (const d of dias) {
      await user.click(d)
      const booked = document.querySelector('.slot.booked')
      if (booked) { await user.click(booked); achou = true; break }
    }
    if (achou) {
      const dlg = await screen.findByRole('dialog')
      expect(within(dlg).getByText(/Marcos Silva|Ana Paula/)).toBeInTheDocument()
      await user.click(within(dlg).getByRole('button', { name: 'Alertar sobre este cliente' }))
      const alerta = await screen.findByRole('dialog')
      await user.click(within(alerta).getByRole('button', { name: 'Enviar alerta' }))
      expect(await within(alerta).findByText('Escolha o motivo do alerta.')).toBeInTheDocument()
      await user.click(within(alerta).getByLabelText('Cliente agressivo'))
      await user.click(within(alerta).getByRole('button', { name: 'Enviar alerta' }))
      expect(await screen.findByText('Alerta enviado ao responsável.', {}, espera)).toBeInTheDocument()
    }
  })
})

describe('historico do cliente', () => {
  it('lista, oculta cancelados e abre avaliacao com tesouras', async () => {
    const user = userEvent.setup()
    window.localStorage.setItem('cl:cliente', JSON.stringify({ token: 'mock.cliente.62955550000', contato: '62955550000' }))
    abrir('/meus-agendamentos')
    expect(await screen.findByRole('heading', { name: 'Meus agendamentos' }, espera)).toBeInTheDocument()
    expect(await screen.findByText('Combo Corte + Barba', {}, espera)).toBeInTheDocument()
    expect(screen.getAllByText('cancelado').length).toBeGreaterThan(0)
    await user.click(screen.getByLabelText('ocultar agendamentos cancelados'))
    expect(screen.queryByText('cancelado')).not.toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'avaliar' }))
    expect(await screen.findByRole('heading', { name: 'Avaliar atendimento' }, espera)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Enviar avaliação' }))
    expect(await screen.findByText('Escolha uma nota de 1 a 5 tesouras.')).toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: /4 tesouras/ }))
    await user.click(screen.getByRole('button', { name: 'Enviar avaliação' }))
    expect(await screen.findByText('Avaliação enviada. Obrigado!', {}, espera)).toBeInTheDocument()
  })
})

describe('login reconhece o cargo', () => {
  it('a mesma tela leva o administrador ao painel e o funcionario a agenda', async () => {
    const user = userEvent.setup()
    for (const [login, destino] of [['admin', '/painel'], ['func', '/agenda']]) {
      window.localStorage.clear()
      const v = abrir('/login')
      await user.type(await screen.findByLabelText('e-mail'), login)
      await user.type(screen.getByLabelText('senha', { exact: true }), login)
      await user.click(screen.getByRole('button', { name: 'Entrar' }))
      await waitFor(() => expect(window.location.pathname).toBe(destino), espera)
      v.unmount()
    }
  })
})

describe('configuracoes: campo de antecedencia', () => {
  it('e compacto e explica a unidade ao lado', async () => {
    window.localStorage.setItem('cl:staff', JSON.stringify({ token: 'mock.staff.u-admin', usuario: { id: 'u-admin', nome: 'Rafael Lima', papel: 'administrador', salaoId: 's1', profissionalId: 'p1' } }))
    abrir('/painel/configuracoes')
    const campo = await screen.findByLabelText('antecedência mínima para o cliente cancelar', {}, espera)
    expect(campo).toHaveClass('input-xs')
    expect(campo).toHaveValue(4)
    expect(screen.getByText('horas antes do horário marcado')).toBeInTheDocument()
  })
})

describe('capa do portal', () => {
  it('a imagem de capa aparece ao redor do botao de agendar', async () => {
    window.localStorage.setItem('cl:staff', JSON.stringify({ token: 'mock.staff.u-admin', usuario: { id: 'u-admin', nome: 'Rafael Lima', papel: 'administrador', salaoId: 's1', profissionalId: 'p1' } }))
    const { api } = await import('../lib/http')
    const portal = await api.get('/painel/portal', { as: 'staff' })
    await api.put('/painel/portal', { ...portal, capa: 'data:image/png;base64,AAAA' }, { as: 'staff' })
    abrir('/barbearia-do-rafael')
    const botao = await screen.findByRole('link', { name: 'Agendar horário' }, espera)
    const hero = botao.closest('.hero')
    expect(hero).toHaveClass('com-capa')
    expect(hero.style.backgroundImage).toContain('data:image/png;base64,AAAA')
  })
  it('sem capa o hero mantem a cor do tema', async () => {
    abrir('/barbearia-do-rafael')
    const botao = await screen.findByRole('link', { name: 'Agendar horário' }, espera)
    expect(botao.closest('.hero')).not.toHaveClass('com-capa')
  })
  it('o painel oferece o envio da capa', async () => {
    window.localStorage.setItem('cl:staff', JSON.stringify({ token: 'mock.staff.u-admin', usuario: { id: 'u-admin', nome: 'Rafael Lima', papel: 'administrador', salaoId: 's1', profissionalId: 'p1' } }))
    abrir('/painel')
    expect(await screen.findByText(/imagem de capa do portal/, {}, espera)).toBeInTheDocument()
    expect(screen.getByText('Clique para enviar a imagem de capa')).toBeInTheDocument()
  })
})

describe('saiba mais em grade', () => {
  it('mostra profissionais, servicos e combos com as informacoes essenciais', async () => {
    abrir('/barbearia-do-rafael/saiba-mais')
    expect(await screen.findByRole('heading', { name: 'Profissionais' }, espera)).toBeInTheDocument()
    const prof = screen.getByRole('heading', { name: 'Rafael' }).closest('article')
    expect(within(prof).getByText('Seg a Sáb, 09h às 18h')).toBeInTheDocument()
    expect(within(prof).getByText('Corte de cabelo', { selector: '.chip' })).toBeInTheDocument()
    const barba = screen.getByRole('heading', { name: 'Barba' }).closest('article')
    expect(within(barba).getByText('R$ 35,00')).toBeInTheDocument()
    expect(within(barba).getByText('30 min')).toBeInTheDocument()
    expect(within(barba).getByText('Rafael')).toBeInTheDocument()
    const combo = screen.getByRole('heading', { name: 'Combo Corte + Barba' }).closest('article')
    expect(within(combo).getByText('R$ 70,00')).toBeInTheDocument()
    expect(within(combo).getByText(/economize R\$ 10,00/)).toBeInTheDocument()
    expect(screen.getByText('Seg a Sáb, 09h às 19h')).toBeInTheDocument() // funcionamento do salao
    expect(document.querySelectorAll('.grid-cards').length).toBeGreaterThanOrEqual(3)
  })
})

describe('notificacoes abrem em modal', () => {
  const entrarComo = (tipo) => window.localStorage.setItem('cl:staff', JSON.stringify(tipo === 'adm'
    ? { token: 'mock.staff.u-admin', usuario: { id: 'u-admin', nome: 'Rafael Lima', papel: 'administrador', salaoId: 's1', profissionalId: 'p1' } }
    : { token: 'mock.staff.u-func', usuario: { id: 'u-func', nome: 'Rafael Lima', papel: 'funcionario', salaoId: 's1', profissionalId: 'p1' } }))

  it('o painel do sino so lista; clicar abre o modal da notificacao e marca como lida', async () => {
    const user = userEvent.setup()
    entrarComo('adm')
    abrir('/painel')
    await user.click(await screen.findByRole('button', { name: /Notificações, 3 não lidas/ }, espera))
    const menu = await screen.findByRole('menu', { name: 'Notificações' })
    expect(within(menu).queryByRole('button', { name: /enviar|pedir/i })).not.toBeInTheDocument() // sem botao de acao na lista
    await user.click(within(menu).getByRole('menuitem', { name: /Atendimento daqui a 30 minutos/ }))
    const dlg = await screen.findByRole('dialog')
    expect(within(dlg).getByRole('heading', { name: 'Atendimento daqui a 30 minutos' })).toBeInTheDocument()
    expect(within(dlg).getByText('Marcos Silva')).toBeInTheDocument()
    expect(within(dlg).getByRole('button', { name: 'Enviar aviso à equipe' })).toBeInTheDocument()
    expect(within(dlg).queryByRole('button', { name: /avaliação/i })).not.toBeInTheDocument() // pedir avaliacao e so do tipo avaliacao
    await user.click(within(dlg).getByRole('button', { name: 'Fechar' }))
    expect(await screen.findByRole('button', { name: /Notificações, 2 não lidas/ })).toBeInTheDocument()
  })
  it('responsavel envia aviso a equipe; avaliacao tem a propria acao', async () => {
    const user = userEvent.setup()
    entrarComo('adm')
    abrir('/painel')
    await user.click(await screen.findByRole('button', { name: /Notificações/ }, espera))
    await user.click(await screen.findByRole('menuitem', { name: /Agendamento cancelado/ }, espera))
    let dlg = await screen.findByRole('dialog')
    await user.click(within(dlg).getByRole('button', { name: 'Enviar aviso à equipe' }))
    expect(await within(dlg).findByLabelText('Juliana Matos', {}, espera)).toBeChecked()
    expect(within(dlg).queryByLabelText('Rafael Lima')).not.toBeInTheDocument() // nao manda aviso para si mesmo
    await user.click(within(dlg).getByRole('button', { name: 'Enviar aviso' }))
    expect(await screen.findByText('Aviso enviado a 1 profissional.', {}, espera)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: /Notificações/ }))
    await user.click(await screen.findByRole('menuitem', { name: /Atendimento concluído/ }, espera))
    dlg = await screen.findByRole('dialog')
    await user.click(within(dlg).getByRole('button', { name: 'Pedir avaliação ao cliente' }))
    expect(await screen.findByText('Link de avaliação enviado ao cliente.', {}, espera)).toBeInTheDocument()
  })
  it('funcionario so le a notificacao: sem botoes de envio', async () => {
    const user = userEvent.setup()
    entrarComo('func')
    abrir('/agenda')
    await user.click(await screen.findByRole('button', { name: /Notificações/ }, espera))
    await user.click(await screen.findByRole('menuitem', { name: /Aviso do responsável/ }, espera))
    const dlg = await screen.findByRole('dialog')
    expect(within(dlg).getByText(/Reunião de alinhamento/, { selector: 'p' })).toBeInTheDocument()
    expect(within(dlg).getAllByRole('button').map((b) => b.getAttribute('aria-label') || b.textContent)).toEqual(['Fechar janela', 'Fechar']) // so o X e Fechar
  })
})

describe('calendario e horarios do administrador', () => {
  const entrar = () => window.localStorage.setItem('cl:staff', JSON.stringify({ token: 'mock.staff.u-admin', usuario: { id: 'u-admin', nome: 'Rafael Lima', papel: 'administrador', salaoId: 's1', profissionalId: 'p1' } }))

  it('a pagina de agendamentos tem a aba de calendario e horarios', async () => {
    const user = userEvent.setup()
    entrar()
    abrir('/painel/agendamentos')
    await user.click(await screen.findByRole('tab', { name: 'Calendário e horários' }, espera))
    expect(await screen.findByRole('heading', { name: /Horário semanal de funcionamento/ }, espera)).toBeInTheDocument()
    const opcoes = screen.getAllByRole('option').map((o) => o.textContent)
    expect(opcoes).toEqual(['Salão inteiro (horário de funcionamento)', 'Rafael Lima (você)', 'Juliana Matos'])
  })
  it('edita o horario de funcionamento do salao', async () => {
    const user = userEvent.setup()
    entrar()
    abrir('/painel/agendamentos?aba=horarios')
    const domingo = await screen.findByRole('switch', { name: 'Domingo: não atende' }, espera)
    const salvar = screen.getByRole('button', { name: 'Salvar horários' })
    expect(salvar).toBeDisabled() // nada alterado ainda
    await user.click(domingo)
    expect(screen.getByRole('switch', { name: 'Domingo: atende' })).toBeInTheDocument()
    expect(salvar).toBeEnabled()
    await user.click(salvar)
    expect(await screen.findByText('Horário de funcionamento salvo.', {}, espera)).toBeInTheDocument()
  })
  it('edita o horario do proprio administrador e de um funcionario', async () => {
    const user = userEvent.setup()
    entrar()
    abrir('/painel/agendamentos?aba=horarios')
    await screen.findByRole('switch', { name: 'Domingo: não atende' }, espera)
    await user.selectOptions(screen.getByLabelText(/agenda que você quer editar/), 'p1')
    expect(await screen.findByRole('heading', { name: 'Horário semanal de Rafael Lima' })).toBeInTheDocument()
    await user.click(screen.getByRole('switch', { name: 'Sábado: atende' }))
    await user.click(screen.getByRole('button', { name: 'Salvar horários' }))
    expect(await screen.findByText('Horário de Rafael Lima salvo.', {}, espera)).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText(/agenda que você quer editar/), 'p2')
    expect(await screen.findByRole('heading', { name: 'Horário semanal de Juliana Matos' })).toBeInTheDocument()
  })
  it('fecha um dia do salao pelo calendario', async () => {
    const user = userEvent.setup()
    entrar()
    abrir('/painel/agendamentos?aba=horarios')
    const cal = await screen.findByRole('group', { name: 'Calendário de Salão' }, espera)
    const livres = await waitFor(() => {
      const b = within(cal).getAllByRole('button').filter((x) => /^\d+ de \w+$/.test(x.getAttribute('aria-label') || '') && !x.disabled)
      expect(b.length).toBeGreaterThan(0)
      return b
    }, espera)
    await user.click(livres[livres.length - 1])
    const dlg = await screen.findByRole('dialog')
    expect(within(dlg).getByRole('heading', { name: 'Fechar o salão neste dia' })).toBeInTheDocument()
    await user.type(within(dlg).getByLabelText('motivo'), 'Reforma')
    await user.click(within(dlg).getByRole('button', { name: 'Salvar bloqueio' }))
    expect(await screen.findByText(/Dia fechado/, {}, espera)).toBeInTheDocument()
  })
})

describe('atalho somente teste na agenda do funcionario', () => {
  it('aparece identificado como teste e liga a permissao que o admin controla', async () => {
    const user = userEvent.setup()
    window.localStorage.setItem('cl:staff', JSON.stringify({ token: 'mock.staff.u-func', usuario: { id: 'u-func', nome: 'Rafael Lima', papel: 'funcionario', salaoId: 's1', profissionalId: 'p1' } }))
    abrir('/agenda')
    expect(await screen.findByText('somente teste', {}, espera)).toBeInTheDocument()
    expect(screen.getByText(/No sistema final esta opção não existe nesta tela/)).toBeInTheDocument()
    const chave = await screen.findByRole('switch', { checked: true }, espera)
    expect(chave).toBeInTheDocument()
    await user.click(chave)
    expect(await screen.findByText(/A edição de horários é liberada pelo responsável, em Configurações/, {}, espera)).toBeInTheDocument()
    expect(screen.getByText('somente teste')).toBeInTheDocument()
  })
})

describe('administrador que nao atende clientes', () => {
  const entrar = () => window.localStorage.setItem('cl:staff', JSON.stringify({ token: 'mock.staff.u-admin', usuario: { id: 'u-admin', nome: 'Rafael Lima', papel: 'administrador', salaoId: 's1', profissionalId: 'p1' } }))

  it('o responsavel pode se tirar da lista de quem atende, e isso aparece na equipe e nos horarios', async () => {
    const user = userEvent.setup()
    entrar()
    abrir('/painel/profissionais')
    const linha = (await screen.findByText('Rafael Lima', { selector: 'td' }, espera)).closest('tr')
    expect(within(linha).getByText('administrador')).toBeInTheDocument()
    await user.click(linha)
    const dlg = await screen.findByRole('dialog')
    const atende = within(dlg).getByLabelText(/atende clientes/)
    expect(atende).toBeChecked()
    expect(within(dlg).getByText(/Você é o administrador/)).toBeInTheDocument()
    await user.click(atende)
    expect(within(dlg).getByText(/serão desvinculados ao salvar/)).toBeInTheDocument()
    expect(within(dlg).getByText('Não se aplica a quem não atende clientes.')).toBeInTheDocument()
    await user.click(within(dlg).getByRole('button', { name: 'Salvar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument(), espera)
    await waitFor(() => {
      const depois = screen.getByText('Rafael Lima', { selector: 'td' }).closest('tr')
      expect(within(depois).getByText('não atende clientes')).toBeInTheDocument()
      expect(within(depois).getByText('administrador')).toBeInTheDocument()
    }, espera)
  })
  it('a agenda de horarios explica por que o responsavel nao aparece', async () => {
    entrar()
    const { api } = await import('../lib/http')
    const rafael = (await api.get('/painel/profissionais', { as: 'staff' })).profissionais.find((p) => p.id === 'p1')
    await api.put('/painel/profissionais/p1', { ...rafael, atende: false }, { as: 'staff' })
    abrir('/painel/agendamentos?aba=horarios')
    expect(await screen.findByText(/Sua agenda não aparece na lista/, {}, espera)).toBeInTheDocument()
    const opcoes = screen.getAllByRole('option').map((o) => o.textContent)
    expect(opcoes).toEqual(['Salão inteiro (horário de funcionamento)', 'Juliana Matos'])
  })
})

describe('sino recebe os eventos do sistema', () => {
  it('um novo agendamento do cliente aparece como notificacao e abre em modal', async () => {
    const user = userEvent.setup()
    window.localStorage.setItem('cl:staff', JSON.stringify({ token: 'mock.staff.u-admin', usuario: { id: 'u-admin', nome: 'Rafael Lima', papel: 'administrador', salaoId: 's1', profissionalId: 'p1' } }))
    const { api } = await import('../lib/http')
    const { addDaysISO, todayISO, weekdayOf } = await import('../lib/dates')
    let data = addDaysISO(todayISO(), 12)
    while (weekdayOf(data) !== 2) data = addDaysISO(data, 1) // uma terca bem a frente, sem bloqueio
    const h = (await api.get('/saloes/barbearia-do-rafael/disponibilidade/horarios', { query: { data, profissionais: 'p1', duracao: 30 } })).find((x) => x.livre)
    await api.post('/saloes/barbearia-do-rafael/agendamentos', { itens: [{ servicoId: 'sv2', profissionalId: 'p1' }], data, hora: h.hora, clienteNome: 'Joana Teste', clienteWhats: '(62) 98888-7777' })
    abrir('/painel')
    await user.click(await screen.findByRole('button', { name: /Notificações, 4 não lidas/ }, espera))
    await user.click(await screen.findByRole('menuitem', { name: /Novo agendamento/ }, espera))
    const dlg = await screen.findByRole('dialog')
    expect(within(dlg).getByText('novo agendamento', { selector: '.status-chip' })).toBeInTheDocument()
    expect(within(dlg).getAllByText(/Joana Teste/).length).toBeGreaterThan(0)
  })
})
