/**
 * Recursos SO PARA TESTE (nao fazem parte do sistema final):
 *  - a barra de navegacao do prototipo (components/layout/ProtoBar)
 *  - o atalho "liberar a edicao dos meus horarios" da agenda do funcionario (features/agenda/AtalhoTeste)
 *  - a rota /teste/edicao-horarios da API simulada
 *
 * As marcas __MODO_TESTE__ e __MOSTRAR_PROTO__ sao definidas em vite.config.js e viram true/false LITERAIS na
 * compilacao. Quem usa esses recursos deve escrever a marca DIRETO na condicao, por exemplo:
 *     const AtalhoTeste = __MODO_TESTE__ ? lazy(() => import('./AtalhoTeste')) : null
 * Assim, com false, o bundler remove o import e o arquivo nem entra no deploy.
 * Nao troque a marca por uma variavel importada: o bundler deixa de conseguir descartar o codigo.
 *
 * No arquivo de DEPLOY: frontend/.env.production ja define VITE_MODO_TESTE=false (lido por "npm run build").
 */
export const MODO_TESTE = __MODO_TESTE__
export const MOSTRAR_PROTO = __MOSTRAR_PROTO__
