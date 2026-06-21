# API Gateway - Portal Conecta

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.x-green)](https://spring.io/projects/spring-boot)
[![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud-Gateway-blue)](https://spring.io/projects/spring-cloud-gateway)
[![WebFlux](https://img.shields.io/badge/Spring-WebFlux-brightgreen)](https://docs.spring.io/spring-framework/reference/web/webflux.html)

Este modulo e a borda HTTP do Portal Conecta. Ele oferece uma origem unica para o frontend, valida JWTs emitidos pelo Hub, aplica rate limit quando habilitado e encaminha as requisicoes para os servicos internos por prefixo.

As regras de negocio e a autorizacao contextual continuam dentro dos servicos responsaveis. O gateway faz a validacao de borda e preserva o header `Authorization` para que os servicos continuem aplicando as permissoes do dominio.

## Responsabilidades

| Tema | Responsabilidade do gateway |
| --- | --- |
| Roteamento publico | Publicar `/hub/**`, `/checklist/**`, `/mapa/**` e `/comunicados/**` |
| Prefixo externo | Remover o primeiro segmento antes de encaminhar para o servico |
| CORS | Usar `ALLOWED_ORIGINS` como lista de origens permitidas |
| JWT | Validar token HS256 com `JWT_SECRET` e repassar `Authorization` |
| Rate limit | Aplicar `RequestRateLimiter` com Redis quando `PORTAL_GATEWAY_RATE_LIMIT_ENABLED=true` |
| Correlacao | Criar ou propagar `X-Correlation-Id` |
| Logs | Registrar metodo, caminho, rota, status, duracao e correlation ID |
| Observabilidade | Expor health, info, metrics e Prometheus |
| Erro proprio | Retornar `ApiError` quando o erro for gerado pelo gateway |

## Fora do escopo

- Fazer cache.
- Agregar respostas de multiplos servicos.
- Acessar bancos de dados dos modulos.
- Substituir validacoes de autorizacao dos servicos.
- Reescrever payloads ou contratos dos servicos.

## Mapa de rotas

As rotas ficam em `GatewayRouteConfig` para permitir filtros compartilhados e condicionais. As URLs continuam configuradas por variavel de ambiente.

| Route ID | Prefixo externo | Variavel de destino | Fallback local | Caminho encaminhado | Rate key |
| --- | --- | --- | --- | --- | --- |
| `hub-auth` | `/hub/auth/**` | `HUB_SERVICE_URL` | `http://localhost:8081` | remove `/hub` | IP |
| `hub` | `/hub/**` | `HUB_SERVICE_URL` | `http://localhost:8081` | remove `/hub` | usuario |
| `checklist` | `/checklist/**` | `CHECKLIST_SERVICE_URL` | `http://localhost:8082` | remove `/checklist` | usuario |
| `mapa` | `/mapa/**` | `MAPA_SERVICE_URL` | `http://localhost:8083` | remove `/mapa` | usuario |
| `comunicados` | `/comunicados/**` | `COMUNICADOS_SERVICE_URL` | `http://localhost:8084` | remove `/comunicados` | usuario |

Exemplo:

```http
POST /hub/auth/login
```

e encaminhado para:

```http
POST /auth/login
```

## Variaveis de ambiente

| Variavel | Finalidade | Default local |
| --- | --- | --- |
| `SERVER_PORT` | Porta HTTP do gateway | `8080` |
| `HUB_SERVICE_URL` | URL interna do Hub Core | `http://localhost:8081` |
| `CHECKLIST_SERVICE_URL` | URL interna do Checklist | `http://localhost:8082` |
| `MAPA_SERVICE_URL` | URL interna do Mapa de Sala | `http://localhost:8083` |
| `COMUNICADOS_SERVICE_URL` | URL interna do Comunicados | `http://localhost:8084` |
| `JWT_SECRET` | Segredo Base64 HS256 compartilhado com Hub e servicos | segredo dev |
| `PORTAL_GATEWAY_SECURITY_ENABLED` | Liga/desliga validacao JWT na borda | `true` |
| `PORTAL_GATEWAY_RATE_LIMIT_ENABLED` | Liga/desliga rate limit por Redis | `false` local, `true` em prod |
| `SPRING_DATA_REDIS_HOST` | Host Redis usado pelo rate limit | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Porta Redis usada pelo rate limit | `6379` |
| `PORTAL_GATEWAY_AUTH_RATE_LIMIT_REPLENISH_RATE` | Tokens por segundo para `/hub/auth/**` | `5` |
| `PORTAL_GATEWAY_AUTH_RATE_LIMIT_BURST_CAPACITY` | Pico permitido para `/hub/auth/**` | `10` |
| `PORTAL_GATEWAY_USER_RATE_LIMIT_REPLENISH_RATE` | Tokens por segundo para rotas autenticadas | `30` |
| `PORTAL_GATEWAY_USER_RATE_LIMIT_BURST_CAPACITY` | Pico permitido para rotas autenticadas | `60` |
| `ALLOWED_ORIGINS` | Origens liberadas no CORS, separadas por virgula | `http://localhost:3000,http://localhost:5173` |
| `ALLOWED_METHODS` | Metodos HTTP liberados no CORS | `GET,POST,PUT,PATCH,DELETE,OPTIONS` |
| `ALLOWED_HEADERS` | Headers aceitos no CORS | `Authorization,Content-Type,Accept,X-Correlation-Id` |
| `PORTAL_ENVIRONMENT` | Ambiente usado em logs estruturados | `local` |

As URLs de destino devem ser definidas por ambiente. Os fallbacks existem apenas para desenvolvimento local e nao devem ser usados como configuracao fixa de producao.

## JWT

O gateway valida JWT HS256 usando o mesmo `JWT_SECRET` Base64 usado pelo Hub e pelos servicos.

- `/hub/auth/**`, `/actuator/health`, `/actuator/info` e `/actuator/prometheus` sao publicos.
- As demais rotas exigem `Authorization: Bearer <token>`.
- O header `Authorization` valido e preservado para o servico de destino.
- Desabilite apenas em desenvolvimento com `PORTAL_GATEWAY_SECURITY_ENABLED=false`.

## Rate limit

O rate limit usa `RequestRateLimiter` do Spring Cloud Gateway com Redis.

- `/hub/auth/**` usa chave por IP para proteger login e refresh.
- Rotas autenticadas usam chave por usuario a partir do claim `sub`.
- Se o principal ainda nao existir ou o token nao trouxer usuario, a chave cai para IP.
- `X-Forwarded-For` tem prioridade sobre o endereco remoto para ambientes com proxy.
- Em desenvolvimento local o default e `PORTAL_GATEWAY_RATE_LIMIT_ENABLED=false` para nao exigir Redis.
- Em producao o profile `prod` habilita rate limit por default.

## Correlation ID

O gateway usa o header `X-Correlation-Id`.

- Se o cliente enviar um valor valido, o gateway preserva.
- Se o header estiver ausente, vazio ou invalido, o gateway gera um UUID.
- O valor e encaminhado ao servico de destino.
- O valor tambem volta na resposta.
- Valores maiores que 128 caracteres ou com caracteres fora de `A-Z`, `a-z`, `0-9`, `.`, `_`, `:` e `-` sao descartados.

## Erros

Quando um servico retorna erro, o gateway preserva status e corpo.

Quando o erro nasce no gateway, a resposta segue `ApiError`:

```json
{
  "timestamp": "2026-06-21T21:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Route not found",
  "path": "/rota-inexistente"
}
```

## Observabilidade

| Endpoint | Finalidade |
| --- | --- |
| `/actuator/health` | Health do processo do gateway |
| `/actuator/info` | Informacoes basicas da aplicacao |
| `/actuator/metrics` | Metricas Micrometer |
| `/actuator/prometheus` | Scrape Prometheus |

O health do gateway nao agrega a saude dos servicos internos no MVP. O health do Redis fica desligado localmente por default para nao marcar o gateway como indisponivel quando o rate limit estiver desligado.

## Rodar localmente

Exemplo com todos os destinos em variaveis:

```powershell
$env:SERVER_PORT="8080"
$env:HUB_SERVICE_URL="http://localhost:8081"
$env:CHECKLIST_SERVICE_URL="http://localhost:8082"
$env:MAPA_SERVICE_URL="http://localhost:8083"
$env:COMUNICADOS_SERVICE_URL="http://localhost:8084"
$env:ALLOWED_ORIGINS="http://localhost:5173,http://localhost:3000"
$env:PORTAL_GATEWAY_RATE_LIMIT_ENABLED="false"
mvn spring-boot:run
```

Para validar rate limit localmente, suba um Redis e execute:

```powershell
$env:PORTAL_GATEWAY_RATE_LIMIT_ENABLED="true"
$env:SPRING_DATA_REDIS_HOST="localhost"
$env:SPRING_DATA_REDIS_PORT="6379"
mvn spring-boot:run
```

URLs:

| Recurso | URL |
| --- | --- |
| Gateway | `http://localhost:8080` |
| Health | `http://localhost:8080/actuator/health` |
| Prometheus | `http://localhost:8080/actuator/prometheus` |

## Validar

```powershell
mvn test
```

Testes focados do gateway:

```powershell
mvn test '-Dtest=GatewayRoutingTest,GatewaySecurityTest,GatewayRateLimitRouteTest,RateLimiterConfigTest'
```
