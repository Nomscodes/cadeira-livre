/**
 * Recurso SO PARA TESTE (nao faz parte do sistema final):
 *  - o atalho "liberar a edicao dos meus horarios" da agenda do funcionario (features/agenda/AtalhoTeste)
 *    Ligado apenas na suite de testes (mode "test"). Fora dos testes, o atalho nao existe.
 *
 * A marca __MODO_TESTE__ e definida em vite.config.js e vira true/false LITERAL na compilacao. Quem usa esse
 * recurso deve escrever a marca DIRETO na condicao, por exemplo:
 *     const AtalhoTeste = __MODO_TESTE__ ? lazy(() => import('./AtalhoTeste')) : null
 * Assim, com false, o bundler remove o import e o arquivo nem entra no deploy.
 * Nao troque a marca por uma variavel importada: o bundler deixa de conseguir descartar o codigo.
 */
export const MODO_TESTE = __MODO_TESTE__
