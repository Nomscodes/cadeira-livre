# Backend: Cadeira Livre API

API REST em Java 21 + Spring Boot, organizada **por funcionalidade**; dentro de cada pacote as camadas são
`Controller → Service → Repository → Entity`, com DTOs (`records`) na borda.

## Estrutura

```
src/main/java/br/com/cadeiralivre/api/
├── config/         SecurityConfig, CorsConfig, OpenApiConfig, ClockConfig
├── security/       JwtService, JwtAuthenticationFilter
├── tenant/         TenantContext (barbearia da requisição, lida do JWT)
├── common/         ApiException, ApiExceptionHandler, Slugs
├── auth/           login e cadastros (RF01–RF05)
├── usuario/        Usuario (abstrata) ← Cliente, DonoBarbearia
├── barbearia/      portal público + customização white-label (RF06–RF09)
├── servico/        catálogo de serviços (RF10–RF12)
├── horario/        funcionamento e bloqueios (RF19–RF20)          [Parte 2: service/controller]
├── agendamento/    solicitar, aprovar, recusar (RF13–RF21)
└── avaliacao/      nota do atendimento                            [Parte 2: service/controller]
src/main/resources/
├── application.yaml / application-dev.yaml
└── db/migration/V1__schema_inicial.sql   (Flyway)
```

## Fluxo de uma requisição do painel (ex.: `POST /painel/servicos`)

1. `JwtAuthenticationFilter` lê o `Authorization: Bearer`, valida o JWT e preenche `SecurityContext` + `TenantContext`.
2. `SecurityConfig` confere a rota (`/painel/**` exige papel `DONO`).
3. `Controller` recebe o JSON como DTO e valida com `@Valid`.
4. `Service` aplica as regras, pega a barbearia em `TenantContext.require()` e chama o `Repository`.
5. `Repository` (Spring Data JPA) fala com o PostgreSQL; o schema vem do Flyway.
6. O `Service` devolve um DTO de resposta; erros viram JSON no `ApiExceptionHandler`.

## Rodar local

```bash
docker compose up -d          # na raiz do repositório
cd backend && ./mvnw spring-boot:run
# Swagger: http://localhost:8080/api/swagger-ui.html
```
