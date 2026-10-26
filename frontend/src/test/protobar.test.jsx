import { beforeEach, describe, expect, it } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from '../context/AuthContext'
import { ClienteProvider, useCliente } from '../context/ClienteContext'
import ProtoBar from '../components/layout/ProtoBar'

function Estado() {
  const { pathname } = useLocation()
  const { usuario } = useAuth()
  const { autenticado } = useCliente()
  return <output data-testid="estado">{`${pathname}|${usuario?.papel ?? '-'}|${autenticado ? 'cliente' : '-'}`}</output>
}
const montar = () => render(
  <MemoryRouter initialEntries={['/login']}>
    <AuthProvider><ClienteProvider><ProtoBar /><Estado /></ClienteProvider></AuthProvider>
  </MemoryRouter>,
)
const estado = () => screen.getByTestId('estado').textContent

beforeEach(() => { window.matchMedia = undefined })

describe('barra de prototipo', () => {
  it('lista todas as 17 telas do mockup', () => {
    montar()
    const nav = screen.getByRole('navigation', { name: 'Navegação do protótipo' })
    expect(nav.querySelectorAll('button:not(.reset)')).toHaveLength(17)
  })
  it('abre cada tela ja com o perfil certo', async () => {
    const user = userEvent.setup()
    montar()
    await user.click(screen.getByRole('button', { name: 'Meu portal' }))
    expect(estado()).toBe('/painel|administrador|-')
    await user.click(screen.getByRole('button', { name: 'Minha agenda' }))
    expect(estado()).toBe('/agenda|funcionario|-')
    await user.click(screen.getByRole('button', { name: 'Histórico' }))
    expect(estado()).toBe('/meus-agendamentos|funcionario|cliente')
    await user.click(screen.getByRole('button', { name: 'Login/Cadastro' }))
    expect(estado()).toBe('/login|-|cliente')
    await user.click(screen.getByRole('button', { name: 'Acesso temporário' }))
    expect(estado()).toBe('/acesso|-|-')
    await user.click(screen.getByRole('button', { name: 'Portal oculto' }))
    expect(estado()).toBe('/espaco-aurora|-|-')
  })
  it('marca a tela atual', async () => {
    const user = userEvent.setup()
    montar()
    await user.click(screen.getByRole('button', { name: 'Serviços' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Serviços' })).toHaveAttribute('aria-current', 'page'))
  })
})
