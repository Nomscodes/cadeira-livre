# Cadeira Livre

![Licença](https://img.shields.io/badge/licen%C3%A7a-MIT-blue)
![Java](https://img.shields.io/badge/Java-17%2B-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-backend-6DB33F)
![React](https://img.shields.io/badge/React-frontend-61DAFB)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-banco%20de%20dados-336791)
![Status](https://img.shields.io/badge/status-em%20desenvolvimento-yellow)

Plataforma SaaS multi-tenant de agendamento online **white-label** para barbearias. Cada estabelecimento tem seu próprio portal de marcação de horários, personalizado com logo, cor primária e nome fantasia, para que o cliente final veja a marca da barbearia ao agendar.

Projeto desenvolvido como Projeto Integrador do curso de Análise e Desenvolvimento de Sistemas (ADS) do **SENAI FATESG**, em Goiânia/GO.

---

## Sumário

- [Sobre o projeto](#sobre-o-projeto)
- [Funcionalidades previstas no MVP](#funcionalidades-previstas-no-mvp)
- [Fora do escopo do MVP](#fora-do-escopo-do-mvp)
- [Tecnologias](#tecnologias)
- [Pré-requisitos](#pré-requisitos)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Instalação e execução](#instalação-e-execução)
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

- o **cliente** consulta horários disponíveis, escolhe o serviço e solicita um agendamento;
- o **dono da barbearia** gerencia serviços, horários de funcionamento e as solicitações, aprovando ou recusando cada uma;
- cada barbearia tem um **portal white-label** próprio, com sua identidade visual.

O sistema não processa pagamentos, não emite nota fiscal e não envia notificações automáticas no MVP.

## Funcionalidades previstas no MVP

- Cadastro e autenticação de clientes e de donos de barbearia, com login por JWT
- Personalização do portal: upload de logo, cor primária e nome fantasia
- Catálogo de serviços com descrição, preço e duração
- Cadastro de horários de funcionamento por dia da semana e bloqueio de datas específicas
- Solicitação de agendamento pelo cliente, com estado `PENDENTE`
- Painel do barbeiro para aprovar ou recusar solicitações
- Regra de conflito: um horário só pode ter um agendamento aprovado por vez
- Isolamento de dados entre barbearias (multi-tenant com `barbearia_id`)

Os requisitos completos (RF01 a RF21 e RNF01 a RNF10) estão na especificação do projeto, em [`docs/`](docs/).

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
| Backend | Java 17 e Spring Boot |
| Frontend | React, HTML e CSS |
| Banco de dados | PostgreSQL |
| Autenticação | JWT |
| Documentação da API | Swagger (OpenAPI) |
| Containers | Docker e Docker Compose |
| Versionamento | Git e GitHub |
| Hospedagem (produção) | Railway (backend e banco) e Vercel (frontend) |

## Pré-requisitos

Versões mínimas para ambiente de desenvolvimento, conforme a seção 7 da especificação:

| Ferramenta | Versão |
|---|---|
| Java Development Kit (JDK) | 17 (LTS) ou superior |
| Node.js | 18 ou superior, com npm 9 ou superior |
| Git | 2.40 ou superior |
| Docker Desktop | 4.0 ou superior (com Compose) |
| Editor de código | Visual Studio Code |

Hardware recomendado: processador Intel i5 / AMD Ryzen 5, 8 GB de RAM e 512 GB de SSD.

## Variáveis de ambiente

As variáveis são definidas em um arquivo `.env`, que **nunca** deve ser versionado. Use o arquivo [`.env.example`](.env.example) como modelo:

| Variável | Finalidade |
|---|---|
| `DATABASE_URL` | String de conexão com o PostgreSQL |
| `JWT_SECRET` | Chave para assinar os tokens JWT |
| `API_KEYS` | Chaves de acesso a serviços externos |
| `PORT` | Porta em que a API será executada |

## Instalação e execução

> **Em construção.** Os passos de instalação e execução serão preenchidos assim que o código do backend e do frontend estiver versionado. Esta seção será atualizada junto com a entrega de cada etapa.

Passos previstos:

1. Clonar o repositório: `git clone https://github.com/Nomscodes/cadeira-livre.git`
2. Copiar o modelo de variáveis: `.env.example` para `.env` e preencher os valores
3. Subir o banco de dados com Docker Compose
4. Executar o backend
5. Executar o frontend

## Testes

Os testes unitários e de integração serão documentados aqui quando o código estiver disponível. A meta da especificação (RNF10) é cobertura de testes unitários **superior a 80%** do código crítico.

## Estrutura do repositório

Estrutura prevista (será ajustada conforme o desenvolvimento avança):

```
cadeira-livre/
├── backend/        # API REST em Java e Spring Boot
├── frontend/       # Aplicação React
├── database/       # Scripts SQL (schema e dados iniciais)
├── docs/           # Especificação, diagramas e decisões de arquitetura (ADRs)
├── .github/        # Templates de issue e pull request
├── .env.example    # Modelo de variáveis de ambiente, sem valores reais
└── README.md
```

## Documentação

- [`docs/`](docs/): documento do projeto (TAP, EAP, requisitos, diagramas de classe, ambiente de produção e especificação de interface)
- [`docs/adr/`](docs/adr/): registros de decisões de arquitetura (ADRs)
- [`CONTRIBUTING.md`](CONTRIBUTING.md): guia de contribuição, com fluxo de branches e padrão de commits
- [`CHANGELOG.md`](CHANGELOG.md): histórico de versões
- [`SECURITY.md`](SECURITY.md): como reportar vulnerabilidades

## Fluxo de trabalho e contribuição

O repositório usa três branches permanentes:

| Branch | Função |
|---|---|
| `main` | Versão estável e entregue. Protegida, sem commits diretos |
| `homologacao` | Testes antes de publicar uma versão |
| `desenvolvimento` | Integração do trabalho da equipe |

Cada tarefa é feita em uma branch curta (`feat/nome-da-funcionalidade` ou `fix/nome-do-problema`), criada a partir de `desenvolvimento`, e entra por pull request revisada por pelo menos um integrante. Os commits seguem o padrão [Conventional Commits](https://www.conventionalcommits.org/pt-br/).

Detalhes completos em [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Equipe

| Integrante | Papel no projeto | GitHub |
|---|---|---|
| Cassiano Nunes de Abreu | Gerente do projeto e desenvolvedor | [@Nomscodes](https://github.com/Nomscodes) |
| Caio Nunes de Abreu | UX/UI Designer | [@Caio4breu](https://github.com/Caio4breu) |
| Gabriel Naoki Uto Turigoe | Desenvolvedor Backend | [@GabrielNaokiUT](https://github.com/GabrielNaokiUT) |
| Wyllian Mariano Nogueira | Desenvolvedor Frontend | [@wyllianmn](https://github.com/wyllianmn) |

Projeto orientado pelo Coordenador do Projeto da Faculdade SENAI FATESG.

## Licença

Distribuído sob a licença MIT. Veja [`LICENSE`](LICENSE) para mais detalhes.
