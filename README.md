# Live Chat MS - Real-Time Chat Application

Uma aplicação de **Live Chat em tempo real** utilizando **WebSocket com protocolo STOMP** como message broker, construída com Spring Boot e comunicação bidireccional entre cliente e servidor.

## Objetivo

Demonstrar uma implementação de chat em tempo real usando:
- **WebSocket**: Comunicação bidirecional em tempo real
- **STOMP (Simple Text Oriented Messaging Protocol)**: Protocolo de mensageria simples
- **Spring Boot**: Framework backend
- **JavaScript com StompJS**: Cliente frontend

## Características

- Conexão WebSocket em tempo real  
- Múltiplos usuários simultâneos  
- Distribuição de mensagens para todos os subscribers  
- Interface web responsiva com Bootstrap  
- Protocolo STOMP para comunicação estruturada  
- Sanitização de HTML contra XSS  

## Tecnologias Utilizadas

### Backend
- **Java 21**
- **Spring Boot 4.0.6**
- **Spring WebSocket**
- **Spring Messaging SIMP**

### Frontend
- **HTML5**
- **CSS3 (Custom Styling)**
- **JavaScript (ES6+)**
- **jQuery**
- **Bootstrap 3.3.7**
- **StompJS 7.0.0**

## Pré-requisitos

- **Java 21** ou superior
- **Maven 3.6+**
- Um navegador moderno com suporte a WebSocket

## Como Instalar e Executar

### 1. Clonar o Repositório

```bash
git clone https://github.com/Gabrielsoac/live-chat.git
cd livechatms
```

### 2. Compilar o Projeto

```bash
./mvnw clean install
```

Ou no Windows:

```bash
mvnw.cmd clean install
```

### 3. Executar a Aplicação

```bash
./mvnw spring-boot:run
```

A aplicação será iniciada em `http://localhost:8080`

### 4. Acessar o Chat

Abra seu navegador e acesse:

```
http://localhost:8080
```

## Como Usar

### Passo 1: Conectar-se ao Chat

1. Insira um **username** no campo "Username"
2. Clique no botão **"Connect"**
3. Você verá a mensagem "Connected" no console do navegador

### Passo 2: Enviar Mensagens

1. Digite sua mensagem no campo **"Message"**
2. Clique em **"Send"** ou pressione Enter
3. A mensagem será entregue a todos os usuários conectados

### Passo 3: Desconectar

1. Clique no botão **"Disconnect"**
2. Você será desconectado do chat

## Arquitetura do Projeto

```
src/
├── main/
│   ├── java/tech/gabrielsoac/livechatms/
│   │   ├── LivechatmsApplication.java          # Ponto de entrada
│   │   ├── config/
│   │   │   └── WebSocketConfig.java            # Configuração WebSocket/STOMP
│   │   ├── controller/
│   │   │   └── LiveChatController.java         # Handler de mensagens
│   │   └── domain/
│   │       ├── ChatInput.java                  # Record DTO de entrada
│   │       └── ChatOutput.java                 # Record DTO de saída
│   └── resources/
│       ├── static/
│       │   ├── index.html                      # Interface web
│       │   ├── app.js                          # Cliente STOMP
│       │   └── main.css                        # Estilos
│       └── application.properties              # Configuração
└── test/
    └── java/.../livechatms/
        └── LivechatmsApplicationTests.java     # Testes
```

## Fluxo de Comunicação

```
┌─────────────────────────────────────────────────────────┐
│                                                           │
│  Cliente 1 (Browser)                                     │
│  ├─ WebSocket Connect                                   │
│  └─ Subscribe: /topics/livechat                         │
│                                                           │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  Spring Boot Server                                      │
│  ├─ WebSocketConfig (Configuração)                      │
│  │  ├─ Endpoint: /gabrielsoac-livechat                  │
│  │  ├─ Message Broker: /topics                          │
│  │  └─ App Destination: /app                            │
│  │                                                        │
│  └─ LiveChatController                                  │
│     ├─ @MessageMapping("/new-message")                  │
│     └─ @SendTo("/topics/livechat")                      │
│                                                           │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  Cliente 2 (Browser)                                     │
│  ├─ WebSocket Connect                                   │
│  └─ Subscribe: /topics/livechat                         │
│                                                           │
└─────────────────────────────────────────────────────────┘

Fluxo de Mensagem:
1. Cliente publica mensagem em: /app/new-message
2. Controller mapeia e processa
3. Controller retorna resposta para: /topics/livechat
4. Message Broker distribui para todos os subscribers
5. Todos os clientes recebem a mensagem
```

## Componentes Principais

### WebSocketConfig.java

Configura o suporte a WebSocket e STOMP:
- Define o endpoint: `/gabrielsoac-livechat`
- Ativa o message broker simples em `/topics`
- Define prefixo de aplicação: `/app`

### LiveChatController.java

Handler de mensagens STOMP:
- Mapeia mensagens enviadas para `/app/new-message`
- Processa o input sanitizando HTML (proteção XSS)
- Distribui para `/topics/livechat`

### app.js

Cliente STOMP em JavaScript:
- Inicializa cliente STOMP
- Gerencia conexão WebSocket
- Publica mensagens
- Subscreve a tópicos

### index.html

Interface web:
- Formulário de login
- Área de chat
- Botões de controle

## Segurança

- **Sanitização de HTML**: `HtmlUtils.htmlEscape()` previne XSS
- **Message Broker em Memória**: Simples, seguro para desenvolvimento
- **WebSocket seguro**: Use `wss://` em produção com certificados SSL/TLS

## Protocolo STOMP

### Comandos Utilizados

| Comando | Direção | Exemplo |
|---------|---------|---------|
| CONNECT | Client → Server | `CONNECT\n...` |
| SUBSCRIBE | Client → Server | `SUBSCRIBE\ndestination:/topics/livechat` |
| SEND | Client → Server | `SEND\ndestination:/app/new-message` |
| MESSAGE | Server → Client | `MESSAGE\ndestination:/topics/livechat` |

## Interface de Usuário

A interface apresenta:
- **Header**: Título "Live Chat"
- **Seção de Conexão**: Username, botões Connect/Disconnect
- **Seção de Mensagem**: Campo de entrada e botão Send
- **Área de Chat**: Tabela com histórico de mensagens
- **Tema Dark**: Fundo escuro com cores roxas (#6968d4)

## Configurações

### application.properties

```properties
spring.application.name=livechatms
```

### Porta Padrão
- **8080** (configuração padrão do Spring Boot)

## Testando a Aplicação

### Teste Manual
1. Abra 2+ abas do navegador em `http://localhost:8080`
2. Conecte com usernames diferentes
3. Envie mensagens de uma aba
4. Verifique que aparecem em todas as abas

### Usando DevTools
- Abra **F12** → **Console**
- Observe os logs de conexão STOMP
- Verifique as mensagens WebSocket em **Network** → **WS**

## Troubleshooting

### Mensagens não chegam
- Verifique a conexão STOMP no console
- Confirme que o username foi preenchido
- Verifique que o endpoint está correto

### Erro 404 no acesso a http://localhost:8080
- Certifique-se que o servidor está rodando
- Verifique a porta (padrão é 8080)
- Veja logs do Spring Boot

## Estrutura de Dados

### ChatInput (Request)
```json
{
  "username": "joao",
  "message": "Olá, mundo!"
}
```

### ChatOutput (Response)
```json
{
  "content": "joao: Olá, mundo!"
}
```

## Autor

**GabrielSoac**  
GitHub: [@gabrielsoac](https://github.com/gabrielsoac)
