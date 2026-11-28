# Cadeira Livre

![Licença](https://img.shields.io/badge/licen%C3%A7a-MIT-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)
![React](https://img.shields.io/badge/React-19-61DAFB)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)
![Versão](https://img.shields.io/badge/vers%C3%A3o-v0.9.0-blue)
![Status](https://img.shields.io/badge/status-em%20homologa%C3%A7%C3%A3o-orange)

Plataforma SaaS multi-tenant de agendamento online **white-label** para barbearias e salões. Cada estabelecimento tem seu próprio portal de marcação de horários, personalizado com logo, cor primária e nome fantasia, para que o cliente final veja a marca do salão ao agendar.

Projeto desenvolvido como Projeto Integrador do curso de Análise e Desenvolvimento de Sistemas (ADS) do **SENAI FATESG**, em Goiânia/GO.

---

## Situação atual: em homologação

A versão **v0.9.0** está na branch **`homologacao`**, que funciona como o ambiente de **QA, testes finais e homologação** do projeto. É nela que o sistema é validado antes de ir para a versão final.

- Backend, frontend e banco de dados estão **integrados e funcionando**, rodando em ambiente local.
- Esta versão foi promovida da `desenvolvimento` para a `homologacao` em 15/11/2026 e validada na apresentação ao professor da matéria em 16/11/2026.
- **Próxima etapa:** deploy (Railway e Vercel). Na banca do Projeto Integrador, a versão final vai estar na `main` e no ar, como **v1.0.0**.

| Versão | Branch | Situação |
|---|---|---|
| v0.8.0 | `desenvolvimento` | Integração de backend, frontend e banco concluída |
| **v0.9.0** | **`homologacao`** | **Versão em homologação (atual)** |
| v1.0.0 | `main` | Versão final, publicada (prevista para a banca) |

O que mudou em cada versão está no [`CHANGELOG.md`](CHANGELOG.md).

---

## Sumário

- [Situação atual: em homologação](#situação-atual-em-homologação)
- [Sobre o projeto](#sobre-o-projeto)
- [Funcionalidades](#funcionalidades)
- [Fora do escopo do MVP](#fora-do-escopo-do-mvp)
- [Tecnologias](#tecnologias)
- [Pré-requisitos](#pré-requisitos)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Instalação e execução](#instalação-e-execução)
- [Contas de demonstração](#contas-de-demonstração)
- [Testes](#testes)
- [Estrutura do repositório](#estrutura-do-repositório)
- [Documentação](#documentação)
- [Fluxo de trabalho e contribuição](#fluxo-de-trabalho-e-contribuição)
- [Equipe](#equipe)
- [Licença](#licença)

---

## Sobre o projeto

Muitas barbearias de pequeno e médio porte ainda controlam agendamentos em agendas físicas ou por trocas de mensagens. Isso gera conflitos de horário, filas desnecessárias e clientes sem saber se há disponibilidade.

O Cadeira Livre centraliza esse processo em uma plataforma online em que:

- o **cliente** consulta horários disponíveis, escolhe os serviços e solicita um agendamento;
- o **dono do salão** gerencia serviços, equipe, horários de funcionamento e as solicitações, aprovando ou recusando cada uma;
- o **funcionário** acompanha a própria agenda;
- cada salão tem um **portal white-label** próprio, com sua identidade visual.

O sistema não processa pagamentos e não emite nota fiscal. As notificações acontecem dentro do sistema (sino de notificações); o envio por SMS, e-mail ou WhatsApp está fora do escopo.

## Funcionalidades

Implementadas na versão v0.9.0:

- Cadastro e autenticação de clientes, donos e funcionários, com login por JWT e logout com encerramento da sessão
- Personalização do portal: logo, capa, galeria, cor primária, nome fantasia, sobre, redes sociais, validação e publicação
- Busca de salões por cidade, categoria, serviço e nome
- Catálogo de serviços com descrição, preço, duração e destaque, e combos de serviços
- Equipe de profissionais, com horário próprio e acesso do funcionário por código de convite
- Horários de funcionamento por dia da semana, folgas e bloqueio de datas específicas
- Solicitação de agendamento com um ou mais serviços e um profissional para cada um, com estado `PENDENTE`
- Painel do dono para aprovar, recusar e concluir solicitações
- Regra de conflito: um horário só pode ter um agendamento aprovado por vez (com trava no banco)
- Área do cliente: histórico, cancelamento no prazo definido pelo salão e avaliação do atendimento
- Notificações dentro do sistema para dono, cliente e funcionário, com lembretes automáticos
- Agenda do funcionário: atendimentos do dia, calendário do mês, descansos e alertas para o dono
- Recuperação de senha em 3 passos (em desenvolvimento, o código aparece no log da API)
- LGPD: aceite dos termos, e-mail e telefone criptografados no banco, apagar dados e encerrar conta
- Auditoria das alterações importantes
- Isolamento de dados entre salões (multi-tenant com `barbearia_id`, vindo do token)

Os requisitos completos (RF01 a RF21 e RNF01 a RNF10) estão no Documento de Projeto (versão 7).

## Fora do escopo do MVP

- Pagamento online integrado
- Notificações automáticas por SMS ou e-mail
- Aplicativo mobile nativo
- Integração com agendas externas (Google Calendar, Outlook)
- Dashboard analítico avançado
- Ações de marketing em redes sociais e emissão de nota fiscal

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security e Spring Data JPA |
| Frontend | React 19 e Vite, com HTML e CSS |
| Banco de dados | PostgreSQL 16, com migrations pelo Flyway |
| Autenticação | JWT, com sessão salva no banco (logout) |
| Segurança dos dados | AES-GCM para e-mail e telefone e BCrypt para senhas |
| Documentação da API | Swagger (OpenAPI) |
| Testes | JUnit 5, Mockito e JaCoCo (backend); Vitest e Testing Library (frontend) |
| Containers | Docker e Docker Compose |
| Versionamento | Git e GitHub |
| Hospedagem (produção) | Railway (backend e banco) e Vercel (frontend) |

## Pré-requisitos

| Ferramenta | Versão |
|---|---|
| Java Development Kit (JDK) | 21 (LTS) |
| Node.js | 20.19 ou superior, com npm |
| Git | 2.40 ou superior |
| Docker Desktop | 4.0 ou superior (com Compose) |
| Editor de código | Visual Studio Code |

O Maven não precisa ser instalado: o backend usa o Maven Wrapper (`mvnw`).

Hardware recomendado: processador Intel i5 / AMD Ryzen 5, 8 GB de RAM e 512 GB de SSD.

## Variáveis de ambiente

As variáveis ficam em arquivos `.env`, que **nunca** devem ser versionados. Use os modelos `.env.example` como base.

**Backend** ([`.env.example`](.env.example)):

| Variável | Finalidade |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil do Spring (`dev` só na máquina local) |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexão com o PostgreSQL |
| `JWT_SECRET` | Chave para assinar os tokens JWT |
| `CRYPTO_KEY` | Chave da criptografia de e-mail e telefone |
| `API_KEYS` | Chaves de acesso a serviços externos |
| `PORT` | Porta em que a API será executada (padrão 8080) |
| `CORS_ORIGINS` | Endereço do frontend que pode chamar a API |

No ambiente local, o perfil `dev` já traz valores de desenvolvimento, então o backend roda sem `.env`.

**Frontend** ([`frontend/.env.example`](frontend/.env.example)):

| Variável | Finalidade |
|---|---|
| `VITE_API_URL` | Endereço da API. Em desenvolvimento não é obrigatória, porque o Vite encaminha `/api` para o backend. Em outros ambientes, use `http://localhost:8080/api` ou a URL da API publicada |
| `VITE_SUPPORT_URL` | Link do canal de suporte (opcional) |

## Instalação e execução

1. Clonar o repositório e entrar na branch de homologação:
   ```bash
   git clone https://github.com/Nomscodes/cadeira-livre.git
   cd cadeira-livre
   git switch homologacao
   ```
2. Subir o banco de dados (PostgreSQL na porta **5433**):
   ```bash
   docker compose up -d
   ```
3. Executar o backend (API na porta **8080**):
   ```bash
   cd backend
   mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
   ```
4. Em outro terminal, executar o frontend (porta **5173**):
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
   Em desenvolvimento, o Vite encaminha as chamadas `/api` para o backend (porta 8080), então não é preciso criar o `.env`. Só crie `frontend/.env` (a partir do `.env.example`) se quiser apontar para outra URL da API.
5. Com o banco vazio, povoar com os dados de demonstração (com a API rodando, na raiz do projeto):
   ```bash
   node database/povoar-pela-api.mjs
   ```
   O script só cria dados quando o banco está vazio. Se já houver salões, ele apenas avisa e não altera nada.
6. Abrir `http://localhost:5173`.

### Zerar o banco e povoar de novo

Use este passo quando quiser começar do zero. **Atenção:** apaga todos os dados do banco local.

1. Pare o backend e o frontend (Ctrl+C nos dois terminais).
2. Na raiz do projeto, zere e suba o banco:
   ```bash
   docker compose down -v
   docker compose up -d
   ```
3. Suba o backend de novo, para ele recriar as tabelas:
   ```bash
   cd backend
   mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
   ```
4. Em outro terminal, na raiz do projeto, povoe o banco:
   ```bash
   node database/povoar-pela-api.mjs
   ```

Endereços úteis:

| O quê | Endereço |
|---|---|
| Sistema | http://localhost:5173 |
| API | http://localhost:8080/api |
| Swagger | http://localhost:8080/api/swagger-ui/index.html |

## Contas de demonstração

Criadas pelo script `database/povoar-pela-api.mjs`. Todas usam a senha `12345678` (só para demonstração).

| Perfil | E-mail | Onde entrar |
|---|---|---|
| Dono (Barbearia Lâmina Clássica) | `admin@teste.com` | `/login` |
| Funcionário (Barbearia Lâmina Clássica) | `func@teste.com` | `/login` |
| Dono (Barbearia Sete Lâminas, com equipe) | `gabriel.naoki@teste.com` | `/login` |
| Dona (Studio Juliana Matos, autônoma) | `juliana.matos@teste.com` | `/login` |
| Cliente | `cliente@teste.com` | `/acesso` ("Sou cliente, quero agendar um horário") |

As demais contas (donos e clientes de outros salões) aparecem na saída do script.

## Testes

| Parte | Comando | Resultado atual |
|---|---|---|
| Backend | `cd backend` e `mvnw test` | 312 testes: 311 passam e 1 está desativado |
| Frontend | `cd frontend` e `npm test` | 126 testes passando |
| Build do frontend | `cd frontend` e `npm run build` | sem erros |

Os testes do frontend usam dados simulados dentro dos próprios testes e não precisam do backend.

A meta da especificação (RNF10) é cobertura de testes unitários **superior a 80%** do código crítico. Na v0.9.0, o backend tem **89%** de cobertura de instruções e **85%** de ramificações, medidas pelo JaCoCo (relatório em `backend/target/site/jacoco/index.html` depois do `mvnw test`).

> O JaCoCo não gera o relatório em pastas com acento no caminho. Nesse caso, use uma letra de unidade temporária, por exemplo `subst X: "C:\caminho\do\cadeira-livre"`, e rode os testes a partir de `X:`.

### Como instalar e rodar os testes

É preciso ter o **JDK 21** e o **Node.js 20.19+**. O **Docker Desktop** só é necessário para o teste de integração. Não precisa deixar a API, o frontend ou o banco local rodando.

**Backend** (o Maven Wrapper baixa as dependências na primeira execução):

```bash
cd backend

# todos os testes
mvnw clean test

# só os testes unitários do agendamento do cliente
mvnw clean test "-Dtest=ClienteAgendamentoServiceTest"

# só os três casos unitários da N2 (U-01, U-02 e U-03)
mvnw clean test "-Dtest=ClienteAgendamentoServiceTest#cancelarPendenteMudaParaCancelado+faltandoMenosDe1HoraNaoDaPraCancelar+faltandoExatamente1HoraAindaDaPraCancelar"

# só o teste de integração C-01 (com o Docker Desktop aberto)
mvnw clean test "-Dtest=CadastroClienteIntegracaoTest"

# só uma classe de teste (troque pelo nome da classe que quiser)
mvnw clean test "-Dtest=AuthServiceTest"
```

No Linux ou macOS, use `./mvnw` em vez de `mvnw`. Os testes do backend ficam em `backend/src/test/java`, separados por funcionalidade (`auth`, `agendamento`, `barbearia`, `horario`, `notificacao` e outras).

| Execução | Resultado esperado |
|---|---|
| Todos os testes, com Docker | `Tests run: 312, Failures: 0, Errors: 0, Skipped: 1` |
| Todos os testes, sem Docker | `Tests run: 312, Failures: 0, Errors: 0, Skipped: 2` |
| Casos U-01, U-02 e U-03 | `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` |
| Unitários do agendamento do cliente | `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0` |
| Integração C-01, com Docker | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` |
| Integração C-01, sem Docker | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 1` |

Todas as execuções terminam com `BUILD SUCCESS`. O teste desativado da suíte é o `CadeiraLivreApiApplicationTests`. Sem Docker, os 2 pulados são esse teste e o C-01.

O C-01 cria o próprio PostgreSQL 16 em um container temporário, aplica as migrations e testa cadastro, login, consulta da conta e a proteção dos dados no banco. Ele não usa o banco local da porta 5433, e o container é removido no fim. Sem Docker, o teste é pulado e o build continua passando.

**Casos da avaliação N2 (Teste de Software):**

| Caso | Tipo | Regra | Classe / método |
|---|---|---|---|
| U-01 | Unitário (resultado esperado) | Cancelar um agendamento pendente muda o status para cancelado e avisa o dono | `ClienteAgendamentoServiceTest#cancelarPendenteMudaParaCancelado` |
| U-02 | Unitário (erro) | Faltando menos de 1 hora, o cancelamento é recusado (422) e o status não muda | `ClienteAgendamentoServiceTest#faltandoMenosDe1HoraNaoDaPraCancelar` |
| U-03 | Unitário (limite) | Faltando exatamente 1 hora, ainda dá para cancelar | `ClienteAgendamentoServiceTest#faltandoExatamente1HoraAindaDaPraCancelar` |
| C-01 | Integração | Cadastro, login e consulta da conta com a API e o PostgreSQL reais; no banco, e-mail cifrado e senha em BCrypt | `CadastroClienteIntegracaoTest` |

**Frontend (Vitest e Testing Library):**

```bash
cd frontend
npm install

# todos os testes
npm test

# só um arquivo de teste
npx vitest run src/test/api.test.js

# build de produção
npm run build
```

| Arquivo em `frontend/src/test` | O que testa | Testes |
|---|---|---|
| `api.test.js` | Regras da API simulada: autenticação, agendamento, horários, painel e notificações | 50 |
| `flows.test.jsx` | Fluxos de tela: login, agendamento, painel, área do cliente e funcionário | 53 |
| `integracao-base.test.jsx` | Contrato com o backend: chamadas, formatos e status dos agendamentos | 13 |
| `protobar.test.jsx` | Barra de protótipo | 3 |
| `tema.test.jsx` | Tema do salão a partir da cor do backend | 5 |
| **Total** | | **124** |

Resultado esperado: `Test Files 5 passed (5)`, `Tests 124 passed (124)` e o build sem erros.

## Estrutura do repositório

```
cadeira-livre/
├── backend/        # API REST em Java e Spring Boot (pacotes por funcionalidade)
├── frontend/       # Aplicação React com Vite (inclui a API simulada usada nos testes)
├── database/       # Script que povoa o banco com dados de demonstração pela API
├── docs/           # Contrato da API, banco de dados, diagramas e decisões de arquitetura
├── .github/        # Templates de issue e pull request e CODEOWNERS
├── docker-compose.yml  # Banco PostgreSQL para desenvolvimento local
├── .env.example    # Modelo de variáveis de ambiente, sem valores reais
├── CHANGELOG.md    # Histórico de versões
└── README.md
```

As migrations do banco (V1 a V20) ficam em `backend/src/main/resources/db/migration` e são aplicadas pelo Flyway quando a API sobe.

## Documentação

- [`docs/api/openapi.yaml`](docs/api/openapi.yaml): contrato da API REST (também disponível no Swagger, com a API rodando)
- [`docs/banco/dicionario-de-dados.md`](docs/banco/dicionario-de-dados.md): dicionário de dados do banco
- [`docs/diagramas/der-cadeira-livre.png`](docs/diagramas/der-cadeira-livre.png): diagrama entidade-relacionamento
- [`docs/adr/`](docs/adr/): registros de decisões de arquitetura (ADRs)
- [`backend/README.md`](backend/README.md): estrutura do backend e fluxo de uma requisição
- [`CONTRIBUTING.md`](CONTRIBUTING.md): guia de contribuição, com fluxo de branches e padrão de commits
- [`CHANGELOG.md`](CHANGELOG.md): histórico de versões
- [`SECURITY.md`](SECURITY.md): como reportar vulnerabilidades

## Fluxo de trabalho e contribuição

O repositório usa três branches permanentes, uma para cada ambiente:

| Branch | Ambiente | Função |
|---|---|---|
| `main` | Produção | Versão estável e entregue. Protegida, sem commits diretos |
| `homologacao` | QA / testes finais / homologação | Validação de uma versão antes de publicar. **Versão atual: v0.9.0** |
| `desenvolvimento` | Desenvolvimento | Integração do trabalho da equipe |

Cada tarefa é feita em uma branch curta (`feat/nome-da-funcionalidade` ou `fix/nome-do-problema`), criada a partir de `desenvolvimento`, e entra por pull request revisada por pelo menos um integrante. Os commits seguem o padrão [Conventional Commits](https://www.conventionalcommits.org/pt-br/).

Quando uma versão fica pronta, a `desenvolvimento` é promovida para a `homologacao` por pull request (com merge commit, para manter o histórico) e recebe uma tag de versão ([Semantic Versioning](https://semver.org/lang/pt-BR/)). Depois de validada, a versão segue da `homologacao` para a `main`.

Detalhes completos em [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Equipe

| Integrante | Papel no projeto | GitHub |
|---|---|---|
| Cassiano Nunes de Abreu | Gerente do projeto e desenvolvedor | [@Nomscodes](https://github.com/Nomscodes) |
| Caio Nunes de Abreu | UX/UI Designer | [@Caio4breu](https://github.com/Caio4breu) |
| Gabriel Naoki Uto Turigoe | Desenvolvedor Backend | [@GabrielNaokiUT](https://github.com/GabrielNaokiUT) |
| Wyllian Mariano Nogueira | Desenvolvedor Frontend | [@wyllianmn](https://github.com/wyllianmn) |

Projeto orientado pelo Coordenador Alisson Rodrigues, da Faculdade SENAI FATESG.

## Licença

Distribuído sob a licença MIT. Veja [`LICENSE`](LICENSE) para mais detalhes.
