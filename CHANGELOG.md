# Histórico de alterações

Todas as alterações relevantes do Cadeira Livre são registradas neste arquivo.

O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o versionamento segue o [Semantic Versioning](https://semver.org/lang/pt-BR/) (MAJOR.MINOR.PATCH).

## [Não lançado]

## [0.9.0] - 2026-11-15

Versão promovida para a branch `homologacao`, usada na validação do projeto com o professor da matéria (16/11/2026).

### Alterado
- Nova tipografia do frontend: DM Serif Display nos títulos, DM Sans no texto e IBM Plex Mono nos rótulos (#81)
- Tamanhos de fonte em `rem`, com corpo de 16px e mínimo de 12px; o zoom de fonte do navegador passa a funcionar (#81)
- Campos de horário da grade semanal e card de login mais largos, para caber o texto maior (#81)

## [0.8.0] - 2026-11-15

Backend, frontend e banco de dados integrados e rodando em ambiente local.

### Adicionado

#### Repositório
- Estrutura inicial do repositório, com README, licença MIT e guia de contribuição
- Padrões de formatação do código (EditorConfig) e de quebras de linha (GitAttributes)
- Modelo de variáveis de ambiente, sem valores reais
- Modelos de issue para bug, nova funcionalidade e change request
- Modelo de Pull Request com checklist e arquivo CODEOWNERS
- Estrutura de pastas da documentação, com requisitos, diagramas, interface, testes, auditoria, manual, ADRs e change requests
- Rótulos de tipo de mudança (corretiva, evolutiva, adaptativa, preventiva) e de prioridade

#### Backend (Spring Boot 4 e Java 21)
- Cadastro de cliente e de dono com a barbearia, com e-mail único e aceite dos termos (RF01, RF02, RF03 e LGPD)
- Login com JWT e logout com encerramento da sessão (RF04 e RF05)
- E-mail e telefone cifrados no banco (AES-GCM) e senha com BCrypt (RNF03)
- Auditoria das alterações importantes (RNF09)
- Portal white-label: logo, capa, galeria, cor, redes sociais, validação e publicação
- Serviços com destaque e exclusão lógica, e combos de serviços
- Profissionais da equipe, com horário próprio e código de convite
- Horário de funcionamento, folgas e bloqueios por data
- Busca de salões por cidade, categoria, serviço e nome
- Disponibilidade por dia e por horário, somando a duração de vários serviços
- Agendamento com vários serviços e um profissional para cada um; o pedido nasce pendente e o dono aprova, recusa ou conclui, com trava contra horário duplicado (RF21)
- Área do cliente: histórico, cancelamento no prazo do salão, avaliação, troca de senha e exclusão da conta
- Notificações dentro do sistema para dono, cliente e funcionários, com lembretes automáticos e pedido de avaliação
- Conta do funcionário pelo código de convite, com agenda própria, descansos e alertas para o dono
- Configurações do salão, transferência do administrador, apagar dados e encerrar conta
- Recuperação de senha em 3 passos (em desenvolvimento, o código aparece no log da API)
- Mais de 300 testes unitários, com relatório de cobertura (JaCoCo)

#### Frontend (React e Vite)
- Todas as telas integradas com a API, mantendo a API simulada para os testes
- Cadastro e login do cliente com e-mail e senha, logout e status do agendamento (pendente, aprovado, recusado, cancelado e concluído)
- Aprovar, recusar e concluir no painel, e sino de notificações do dono, do cliente e do funcionário
- Tela de primeiro acesso do funcionário com o código de convite
- Tela de recuperar senha

#### Banco de dados (PostgreSQL 16 e Flyway)
- Migrations V1 a V20
- Diagrama e dicionário de dados
- Script `database/povoar-pela-api.mjs`, que cria os dados de demonstração pela API

### Alterado
- Respostas de erro da API no formato `{ message, problemas[] }`
- A área do cliente passa a usar e-mail e senha (RF01 e RF04) no lugar do acesso por código, que dependia de envio por WhatsApp ou e-mail (fora do escopo da V7)

### Corrigido
- Perfil de desenvolvimento deixa de ser o padrão da API e as variáveis de ambiente foram alinhadas
- Token inválido não bloqueia mais as rotas públicas
- Quando uma conta entra na tela errada (dono na área do cliente ou o contrário), a sessão aberta é encerrada

### Fora desta versão
- Deploy do frontend, da API e do banco
- Envio de e-mail e WhatsApp (fora do escopo da V7)
- Armazenamento das fotos enviadas na avaliação

[Não lançado]: https://github.com/Nomscodes/cadeira-livre/compare/v0.9.0...HEAD
[0.9.0]: https://github.com/Nomscodes/cadeira-livre/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/Nomscodes/cadeira-livre/releases/tag/v0.8.0
