# CICD Lab API

API em **Spring Boot 4** (Java 21) com um pipeline de **CI/CD** completo: testes automatizados, build do artefato, empacotamento em imagem Docker, publicação no **GHCR** (GitHub Container Registry) e deploy automático no **Render**.

## Sumário

- [Arquitetura do pipeline](#arquitetura-do-pipeline)
- [Passo a passo do pipeline](#passo-a-passo-do-pipeline)
- [Workflow do GitHub Actions](#workflow-do-github-actions)
- [Dockerfile](#dockerfile)
- [Secrets necessários](#secrets-necessários)
- [Rodando localmente](#rodando-localmente)
- [Deploy no Render](#deploy-no-render)

## Arquitetura do pipeline

```
                            DEVELOPER
                                │
                                │ git push
                                ▼
                            GITHUB
                                │
                                ▼
                         GITHUB ACTIONS
                                │
                 ┌──────────────┴──────────────┐
                 ▼                             ▼
               TEST                           BUILD
                 │                             │
           ┌─────┴─────┐                  application.jar
           ▼           ▼                       │
         UNIT     INTEGRATION                  ▼
           │           │                    ARTIFACT
           └─────┬─────┘                       │
                 ▼                             │
                ✅                             │
                 │                             │
                 └──────────────┬──────────────┘
                                ▼
                              DOCKER
                                │
                                ▼
                          DOCKER IMAGE
                                │
                                ▼
                               GHCR
                                │
                                ▼
                         RENDER DEPLOY HOOK
                                │
                                ▼
                              RENDER
                                │
                                ▼
                            CONTAINER
                                │
                                ▼
                           SPRING BOOT
                                │
                                ▼
                            🌎 INTERNET
```

O pipeline é disparado automaticamente a cada `git push` (ou pull request) para a branch `main`, e é 100% executado pelo **GitHub Actions**, definido em [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

## Passo a passo do pipeline

### 1. DEVELOPER → GITHUB

O desenvolvedor faz `git push` para o GitHub. O push (ou a abertura de um pull request) para `main` é o gatilho (`on.push` / `on.pull_request`) que inicia o workflow.

### 2. GITHUB ACTIONS — job `test`

```yaml
test:
  runs-on: ubuntu-latest
  steps:
    - Checkout do código
    - Setup do Java 21 (Temurin)
    - ./mvnw test
```

Roda `./mvnw test`, que executa **todo** o código em `src/test/java`. No projeto atual isso inclui tanto testes **unitários** (ex.: `HelloControllerTest`) quanto testes de **integração** (ex.: `HelloControllerIntegrationTest`) — o Maven Surefire executa ambos nessa mesma etapa, que é o que o diagrama representa como os ramos `UNIT` e `INTEGRATION` convergindo para o ✅.

Se algum teste falhar, o pipeline para aqui e as etapas seguintes (`build`, `docker`) nunca são executadas, pois dependem de `needs: test`.

A variável `APP_MESSAGE` é injetada nos testes via secret do GitHub.

### 3. GITHUB ACTIONS — job `build`

```yaml
build:
  needs: test
  runs-on: ubuntu-latest
  steps:
    - Checkout do código
    - Setup do Java 21 (Temurin)
    - ./mvnw package
    - Upload do artefato (application.jar)
```

Compila o projeto e gera o `application.jar` em `target/`. Esse `.jar` é publicado como **artefato do GitHub Actions** (`actions/upload-artifact`), ficando disponível para o próximo job sem precisar recompilar.

### 4. GITHUB ACTIONS — job `docker`

```yaml
docker:
  needs: build
  runs-on: ubuntu-latest
  steps:
    - Checkout do código
    - Login no GHCR
    - Download do artefato (application.jar)
    - docker build
    - docker push
    - Trigger do Render Deploy Hook
```

Essa etapa:

1. Faz login no **GHCR** (`ghcr.io`) usando o `GITHUB_TOKEN` automático do Actions.
2. Baixa o `application.jar` gerado no job anterior.
3. Constrói a imagem Docker (`docker build`) usando o [`Dockerfile`](Dockerfile) do repositório.
4. Publica (`docker push`) a imagem em `ghcr.io/<owner>/cicd-lab-api:latest`.
5. Dispara o **deploy hook** do Render via `curl -X POST`, avisando o Render que há uma nova imagem disponível.

### 5. GHCR → RENDER DEPLOY HOOK → RENDER

O **Render** está configurado para usar a imagem publicada no GHCR. Ao receber a chamada do deploy hook, ele:

- Puxa (`pull`) a imagem mais recente (`:latest`) do GHCR.
- Sobe um novo **container** a partir dela.
- Substitui o container antigo pelo novo (deploy).

### 6. CONTAINER → SPRING BOOT → 🌎 INTERNET

Dentro do container, o `Dockerfile` executa:

```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
```

O que sobe a aplicação **Spring Boot** na porta `8080` (exposta via `EXPOSE 8080`). O Render expõe essa aplicação publicamente na internet, através da URL fornecida pela plataforma.

## Workflow do GitHub Actions

Arquivo: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

| Job | Depende de | O que faz |
|---|---|---|
| `test` | — | Roda `./mvnw test` (testes unitários + integração) |
| `build` | `test` | Roda `./mvnw package` e sobe o `.jar` como artefato |
| `docker` | `build` | Builda a imagem Docker, publica no GHCR e dispara o deploy no Render |

**Gatilhos (`on`):**
- `push` na branch `main`
- `pull_request` para a branch `main`

**Permissões:**
- `contents: read`
- `packages: write` (necessário para publicar no GHCR)

## Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

Imagem baseada em **Eclipse Temurin 21 JRE** (somente runtime, mais leve que o JDK completo), que copia o `.jar` já compilado pelo job `build` e executa a aplicação na porta `8080`.

## Secrets necessários

Configurados em **Settings → Secrets and variables → Actions** do repositório no GitHub:

| Secret | Uso |
|---|---|
| `APP_MESSAGE` | Injetada como variável de ambiente durante os testes |
| `GITHUB_TOKEN` | Gerado automaticamente pelo GitHub Actions, usado para autenticar no GHCR |
| `RENDER_DEPLOY_HOOK` | URL do deploy hook do serviço no Render, chamada via `curl -X POST` para disparar o deploy |

## Rodando localmente

```bash
# Rodar os testes
./mvnw test

# Gerar o artefato (.jar)
./mvnw package

# Rodar a aplicação
./mvnw spring-boot:run
```

Com Docker:

```bash
# Buildar a imagem localmente (após ./mvnw package)
docker build -t cicd-lab-api .

# Rodar o container
docker run -p 8080:8080 cicd-lab-api
```

A aplicação sobe em `http://localhost:8080`.

## Deploy no Render

O serviço no Render está configurado para consumir a imagem `ghcr.io/<owner>/cicd-lab-api:latest`. O deploy **não** é feito via push direto de código para o Render — ele acontece exclusivamente quando o **Deploy Hook** é chamado ao final do job `docker` do GitHub Actions, garantindo que só imagens que passaram pelos testes e pelo build cheguem a produção.
