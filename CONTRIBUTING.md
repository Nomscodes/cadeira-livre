# Guia de contribuição

Obrigado por contribuir com o Cadeira Livre. Este guia define como o trabalho é organizado, como as branches são criadas, como os commits são escritos e como as alterações chegam à versão entregue. Seguir estas regras mantém o histórico legível e permite rastrear cada mudança até a sua origem.

---

## 1. Branches permanentes

O repositório tem três branches permanentes:

| Branch | Função | Quem altera |
|---|---|---|
| `main` | Versão estável, a que foi entregue | Somente por Pull Request vindo de `homologacao` |
| `homologacao` | Testes antes de publicar uma versão | Somente por Pull Request vindo de `desenvolvimento` |
| `desenvolvimento` | Integração do trabalho da equipe | Somente por Pull Request de `feat/` ou `fix/` |

**Nunca faça commit direto nestas três branches.** Todo trabalho passa por uma branch de tarefa e por um Pull Request.

---

## 2. Branches de tarefa

Cada tarefa é feita em uma branch curta, criada **a partir de `desenvolvimento`**.

### Nomenclatura

- `feat/nome-da-funcionalidade`: nova funcionalidade
- `fix/nome-do-problema`: correção de defeito
- `docs/nome-do-assunto`: alteração apenas em documentação
- `chore/nome-da-tarefa`: tarefas de manutenção, configuração ou ferramentas

Use letras minúsculas, sem acentos e com hífens entre as palavras.

Exemplos:
- `feat/cadastro-de-clientes`
- `fix/conflito-de-horario-no-agendamento`
- `docs/adr-isolamento-multitenant`

### Como criar

```powershell
git switch desenvolvimento
git pull
git switch -c feat/nome-da-funcionalidade
```

---

## 3. Commits

Os commits seguem o padrão [Conventional Commits](https://www.conventionalcommits.org/pt-br/).

### Formato

```
tipo: descrição curta no imperativo
```

Quando for necessário explicar o motivo, adicione um corpo separado por uma linha em branco.

### Tipos aceitos

| Tipo | Quando usar |
|---|---|
| `feat` | Nova funcionalidade |
| `fix` | Correção de defeito |
| `docs` | Alteração apenas em documentação |
| `refactor` | Reestruturação de código sem mudança de comportamento |
| `test` | Criação ou ajuste de testes |
| `chore` | Configuração, dependências, ferramentas e arquivos de projeto |

### Regras

- Um commit deve representar **uma mudança lógica**.
- Escreva no imperativo: `adiciona`, `corrige`, `remove`, e não `adicionando` ou `adicionei`.
- Não use mensagens genéricas como `ajustes`, `teste` ou `vai`.
- A autoria deve ser a conta do GitHub do integrante (`git config user.name` e `git config user.email`).

### Exemplos

```
feat: adiciona cadastro de horários de funcionamento por dia da semana
fix: impede agendamento duplicado no mesmo horário
docs: adiciona ADR sobre isolamento por barbearia_id
test: cobre regra de aprovação de agendamento
```

---

## 4. Pull Requests

### Antes de abrir

- [ ] A branch está atualizada com `desenvolvimento`
- [ ] O código compila e os testes passam localmente
- [ ] Não há senhas, tokens ou arquivos `.env` nos commits
- [ ] Há uma issue relacionada à mudança (quando aplicável)

### Como abrir

1. Envie a branch: `git push -u origin feat/nome-da-funcionalidade`
2. Abra o Pull Request **com destino em `desenvolvimento`**.
3. Na descrição, relacione a issue com `Closes #N`, quando houver.
4. Preencha o template de Pull Request com o resumo da mudança e como testá-la.

### Revisão

- Todo Pull Request precisa de **pelo menos uma aprovação** de outro integrante da equipe.
- Quem abriu o PR **não aprova o próprio PR**.
- Comentários de revisão devem ser respondidos ou resolvidos antes do merge.
- O merge é feito depois da aprovação, por quem abriu o PR.

---

## 5. Fluxo até a entrega

1. `feat/` ou `fix/` → PR → `desenvolvimento`
2. Quando `desenvolvimento` estiver estável: PR → `homologacao` para testes finais
3. Quando a versão for aprovada em homologação: PR → `main` e criação da tag de versão (`vMAJOR.MINOR.PATCH`)

Depois do merge, apague a branch de tarefa.

---

## 6. Histórico

- Não reescreva histórico (`git push --force`) em `main`, `homologacao` ou `desenvolvimento`.
- Não crie commits de merge vazios.
- Não versione arquivos gerados, binários desnecessários ou arquivos de segredo.

---

## 7. Dúvidas

Em caso de dúvida sobre o fluxo, abra uma issue antes de começar a implementação. Isso evita retrabalho e mantém a rastreabilidade das decisões.
