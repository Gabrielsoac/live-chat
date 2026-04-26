# Design System - Live Chat MS

## Índice

1. [Visão Geral](#visão-geral)
2. [Arquitetura de Sistema](#arquitetura-de-sistema)
3. [Padrões de Design](#padrões-de-design)
4. [Fluxo de Dados](#fluxo-de-dados)
5. [Componentes](#componentes)
6. [Protocolo STOMP](#protocolo-stomp)
7. [Design da UI](#design-da-ui)
8. [Decisões Arquiteturais](#decisões-arquiteturais)

---

## Visão Geral

O **Live Chat MS** é um sistema de chat em tempo real que utiliza:

- **WebSocket**: Para comunicação bidirecional persistente
- **STOMP**: Protocolo de mensageria sobre WebSocket
- **Spring Boot**: Framework backend com suporte nativo
- **Client-side STOMP**: JavaScript com biblioteca StompJS

### Objetivo do Design

Criar uma arquitetura simples, escalável e fácil de entender para comunicação em tempo real.

---

## Arquitetura de Sistema

### Diagrama de Camadas

```
┌─────────────────────────────────────────┐
│         CAMADA DE APRESENTAÇÃO          │
│  (Browser/HTML/CSS/JavaScript)          │
├─────────────────────────────────────────┤
│      CAMADA DE COMUNICAÇÃO (WebSocket)  │
│  (STOMP Protocol via StompJS)           │
├─────────────────────────────────────────┤
│      CAMADA DE APLICAÇÃO (Spring)       │
│  (Controllers, Handlers, Beans)         │
├─────────────────────────────────────────┤
│      CAMADA DE MENSAGERIA (SIMP)        │
│  (Message Broker, Pub/Sub)              │
├─────────────────────────────────────────┤
│       CAMADA DE DOMÍNIO                 │
│  (DTOs, Records, Entidades)             │
└─────────────────────────────────────────┘
```

### Arquitetura Horizontal

```
┌───────────────────────────────────────────────────────────────┐
│                      HTTP SERVER                               │
│                   (Spring Boot 4.0.6)                          │
├──────────────────┬────────────────┬──────────────────────────┤
│                  │                │                          │
│  Static Assets   │  Controllers   │  WebSocket Handler      │
│  ├─ index.html   │  ├─ Routes     │  ├─ Connection Mgmt     │
│  ├─ app.js       │  └─ REST APIs  │  ├─ Message Routing    │
│  ├─ main.css     │                │  └─ Pub/Sub Broker     │
│  └─ jQuery lib   │                │                         │
│                  │                │                         │
└──────────────────┴────────────────┴──────────────────────────┘
           │                │                │
           ▼                ▼                ▼
      [Browser]      [STOMP Handler]   [Message Broker]
```

---

## Padrões de Design

### 1. **Message Broker Pattern**

**Objetivo**: Desacoplar produtores e consumidores de mensagens.

**Implementação**:
- `WebSocketConfig` configura o broker em memória
- Mensagens publicadas em `/topics/livechat`
- Todos os subscribers recebem automaticamente

```java
registry.enableSimpleBroker("/topics");  // Ativa broker simples
registry.setApplicationDestinationPrefixes("/app");  // Define prefixo
```

### 2. **Controller/Handler Pattern**

**Objetivo**: Processar requisições e coordenar respostas.

**Implementação**:
- `LiveChatController` com método anotado
- `@MessageMapping`: mapeia destino de entrada
- `@SendTo`: define destino de saída

```java
@MessageMapping("/new-message")
@SendTo("/topics/livechat")
public ChatOutput newMessage(ChatInput input) { ... }
```

### 3. **Data Transfer Object (DTO) Pattern**

**Objetivo**: Transferir dados entre camadas com segurança de tipo.

**Implementação** (Records Java):
```java
public record ChatInput(String username, String message) { }
public record ChatOutput(String content) { }
```

**Vantagens**:
- Imutáveis por padrão
- Implementam `equals()`, `hashCode()`, `toString()`
- Menor footprint de código

### 4. **Pub/Sub Pattern**

**Objetivo**: Implementar comunicação baseada em tópicos.

**Fluxo**:
1. Clientes se **subscrevem** a `/topics/livechat`
2. Um cliente **publica** para `/app/new-message`
3. Servidor **entrega** para todos os subscribers

### 5. **Proxy Pattern (StompJS Client)**

**Objetivo**: Encapsular comunicação WebSocket complexa.

```javascript
const stompClient = new StompJs.Client({
  brokerURL: "ws://...",
  onConnect: (frame) => { ... },
  onError: (error) => { ... }
});
```

### 6. **Sanitization Pattern**

**Objetivo**: Prevenir XSS (Cross-Site Scripting).

```java
HtmlUtils.htmlEscape(input.username() + ": " + input.message())
```

---

## Fluxo de Dados

### Fluxo Completo de Mensagem

```
┌──────────────────────────────────────────────────────────────┐
│  CLIENTE 1 (Browser Tab)                                      │
│                                                                │
│  1. User digita mensagem e clica "Send"                       │
│  2. app.js chama sendMessage()                                │
│  3. StompJS publica em /app/new-message                       │
│     Payload: { username: "João", message: "Oi!" }            │
│                          │                                     │
│                          ▼                                     │
└──────────────────────────────────────────────────────────────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
    ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
    │ WebSocket   │ │  STOMP      │ │   SIMP      │
    │ Transport   │ │  Protocol   │ │  Broker     │
    └─────────────┘ └─────────────┘ └─────────────┘
           │               │               │
           └───────────────┼───────────────┘
                           │
           ┌───────────────▼───────────────┐
           │                               │
           │  SPRING SERVER                │
           │                               │
           │  WebSocketConfig              │
           │  ├─ Escuta /app/new-message  │
           │  └─ Roteia para Controller   │
           │                               │
           │  LiveChatController           │
           │  ├─ newMessage(ChatInput)    │
           │  ├─ Sanitiza: HtmlEscape()   │
           │  └─ Cria ChatOutput          │
           │                               │
           │  Message Broker               │
           │  └─ Publica em /topics       │
           │                               │
           └───────────────┬───────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
    ┌────────────┐    ┌────────────┐    ┌────────────┐
    │ CLIENTE 1  │    │ CLIENTE 2  │    │ CLIENTE 3  │
    │ (Browser)  │    │ (Browser)  │    │ (Browser)  │
    │            │    │            │    │            │
    │ Recebe:    │    │ Recebe:    │    │ Recebe:    │
    │ "João: Oi!"│    │ "João: Oi!"│    │ "João: Oi!"│
    │            │    │            │    │            │
    │ Atualiza   │    │ Atualiza   │    │ Atualiza   │
    │ tabela chat│    │ tabela chat│    │ tabela chat│
    └────────────┘    └────────────┘    └────────────┘
```

### Diagrama de Sequência

```
Cliente 1          WebSocket        Spring Server      Broker         Cliente 2,3...
   │                   │                  │              │               │
   │──── CONNECT ──────►│                  │              │               │
   │                   │───► CONNECT ────►│              │               │
   │◄─── ACK ──────────│◄──── ACK ────────│              │               │
   │                   │                  │              │               │
   │                   │          SUBSCRIBE /topics     │               │
   │──── SUBSCRIBE ────►│─────────────────►│              │               │
   │                   │                  ├─────────────►│               │
   │                   │                  │              │               │
   │                   │                  │              │              │ │
   │                   │                  │              │    SUBSCRIBE│ │
   │                   │                  │◄─────────────│              │ │
   │                   │                  │              │              │ │
   │                   │                  │              │              │ │
   │  send: /app/msg   │                  │              │              │ │
   │──── SEND ─────────►│                  │              │              │ │
   │                   │───► FORWARD ─────►│              │              │ │
   │                   │          │       │              │              │ │
   │                   │          └─► process()        │              │ │
   │                   │                  │              │              │ │
   │                   │            publish /topics/   │              │ │
   │                   │                  ├─────────────►│              │ │
   │                   │                  │              ├──► MESSAGE ─►│ │
   │                   │                  │              │              │ │
   │                   │ ◄─── MESSAGE ────┤◄─────────────┤              │ │
   │◄─── MESSAGE ──────│                  │              │              │ │
   │  updateLiveChat() │                  │              │              │ │
   │                   │                  │              │              │ │
```

---

## Componentes

### 1. WebSocketConfig

**Responsabilidade**: Configurar STOMP e WebSocket

**Classe**: `tech.gabrielsoac.livechatms.config.WebSocketConfig`

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    // Message Broker Configuration
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topics");           // Topics broadcast
        registry.setApplicationDestinationPrefixes("/app"); // App handlers
    }
    
    // WebSocket Endpoint Registration
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/gabrielsoac-livechat");    // Entry point
    }
}
```

**Métodos Chave**:

| Método | Função |
|--------|--------|
| `enableSimpleBroker()` | Ativa broker em memória para `/topics/*` |
| `setApplicationDestinationPrefixes()` | Define prefixo para routes da app |
| `addEndpoint()` | Registra endpoint WebSocket STOMP |

### 2. LiveChatController

**Responsabilidade**: Processar e distribuir mensagens

**Classe**: `tech.gabrielsoac.livechatms.controller.LiveChatController`

```java
@Controller
public class LiveChatController {
    
    @MessageMapping("/new-message")
    @SendTo("/topics/livechat")
    public ChatOutput newMessage(ChatInput input) {
        return new ChatOutput(
            HtmlUtils.htmlEscape(input.username() + ": " + input.message())
        );
    }
}
```

**Fluxo**:
1. `@MessageMapping`: Cliente publica em `/app/new-message`
2. Spring deserializa JSON para `ChatInput`
3. `newMessage()` é invocado
4. Retorno é serializado para JSON
5. `@SendTo`: Publica em `/topics/livechat`
6. Todos os subscribers recebem

### 3. DTOs (Data Classes)

#### ChatInput

```java
public record ChatInput(String username, String message) { }
```

**Campos**:
- `username`: Nome do remetente
- `message`: Conteúdo da mensagem

#### ChatOutput

```java
public record ChatOutput(String content) { }
```

**Campos**:
- `content`: Mensagem formatada e sanitizada

**Serialização JSON**:
```json
{
  "username": "João",
  "message": "Olá!"
}
```

### 4. StompJS Client (app.js)

**Responsabilidade**: Comunicação cliente-servidor

```javascript
const stompClient = new StompJs.Client({
  brokerURL: "ws://" + window.location.host + "/gabrielsoac-livechat",
  
  onConnect: (frame) => {
    setConnected(true);
    stompClient.subscribe("/topics/livechat", (message) => {
      updateLiveChat(JSON.parse(message.body).content);
    });
  },
  
  onStompError: (frame) => {
    console.error("Broker reported error: " + frame.headers["message"]);
  }
});
```

**Métodos Principais**:

| Método | Função |
|--------|--------|
| `activate()` | Conecta ao servidor WebSocket |
| `deactivate()` | Desconecta do servidor |
| `subscribe()` | Subscreve a um tópico |
| `publish()` | Publica mensagem em destino |
| `unsubscribe()` | Cancela subscrição |

---

## Protocolo STOMP

### Visão Geral

STOMP (Simple Text Oriented Messaging Protocol) é um protocolo de texto simples para Message-Oriented Middleware.

### Frames STOMP Utilizados

#### 1. CONNECT

**Direção**: Cliente → Servidor

**Propósito**: Estabelecer conexão

```
CONNECT
accept-version:1.0,1.1,1.2
heart-beat:0,0
```

**Resposta do Servidor**:
```
CONNECTED
version:1.2
server:RabbitMQ/...
```

#### 2. SUBSCRIBE

**Direção**: Cliente → Servidor

**Propósito**: Subscrever a um tópico

```
SUBSCRIBE
destination:/topics/livechat
id:sub-0
```

#### 3. SEND

**Direção**: Cliente → Servidor

**Propósito**: Publicar mensagem

```
SEND
destination:/app/new-message
content-length:45

{"username":"João","message":"Olá!"}
```

#### 4. MESSAGE

**Direção**: Servidor → Cliente

**Propósito**: Entregar mensagem do tópico

```
MESSAGE
destination:/topics/livechat
message-id:ID:...
subscription:sub-0

{"content":"João: Olá!"}
```

#### 5. DISCONNECT

**Direção**: Cliente → Servidor

**Propósito**: Encerrar conexão

```
DISCONNECT
receipt:77
```

### Endpoints STOMP

| Endpoint | Tipo | Função |
|----------|------|--------|
| `/gabrielsoac-livechat` | Connection | Entrada para WebSocket |
| `/app/new-message` | Handler | Publica mensagem nova |
| `/topics/livechat` | Broadcast | Recebe todas mensagens |

---

## Design da UI

### Estrutura HTML

```
┌─────────────────────────────────────────────────────┐
│  Live Chat                                          │
├─────────────────────────────────────────────────────┤
│                                                      │
│  Username: [___________]                            │
│  Message: [_____________________] [Send]            │
│                                                      │
│  [Connect] [Disconnect]                             │
│                                                      │
├─────────────────────────────────────────────────────┤
│  Live Chat                                          │
├─────────────────────────────────────────────────────┤
│  João: Olá!                                         │
│  Maria: E aí?                                       │
│  João: Tudo bem?                                    │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### Paleta de Cores

| Elemento | Cor | Hex | Uso |
|----------|-----|-----|-----|
| Background | Roxo Escuro | #171831 | Fundo principal |
| Container | Roxo Médio | #1f2044 | Área de conteúdo |
| Títulos | Roxo Claro | #6968d4 | Headers, destaque |
| Texto | Branco | #ffffff | Conteúdo texto |
| Placeholder | Cinza | #c2c2c2 | Inputs vazios |
| Botão Connect | Verde | #28a745 | Ação positiva |
| Botão Disconnect | Vermelho | #dc3545 | Ação negativa |
| Botão Send | Roxo | #6968d4 | Ação primária |

### Responsive Design

- **Desktop**: Layout com colunas, máx 1200px
- **Tablet**: 768px, ajuste de colunas
- **Mobile**: Stack vertical, full-width

### Componentes UI

#### Input Fields
```css
.form-control {
  border-radius: 25px;
  padding: 10px 20px;
  border: 1px solid #6968d4;
  background-color: transparent;
  color: white;
}
```

#### Buttons
```css
.btn {
  border-radius: 25px;
  padding: 10px 20px;
  box-shadow: 0px 4px 10px rgba(0, 0, 0, 0.2);
  border: none;
}

.btn-connect { background-color: #28a745; }
.btn-disconnect { background-color: #dc3545; }
.btn-primary { background-color: #6968d4; }
```

#### Chat Area
```css
#conversation {
  border-radius: 12px;
  box-shadow: 0px 4px 20px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}
```

---

## 🏛️ Decisões Arquiteturais

### 1. Por que WebSocket?

**Alternativas Consideradas**:
- Long Polling: Ineficiente, muitas requisições
- Server-Sent Events (SSE): Apenas servidor → cliente
- REST Polling: Latência alta, overhead

**Decisão**: WebSocket para comunicação full-duplex em tempo real

### 2. Por que STOMP?

**Alternativas Consideradas**:
- WebSocket bruto: Protocolo de baixo nível
- JSON-RPC: Mais complexo
- GraphQL Subscriptions: Overhead desnecessário

**Decisão**: STOMP por simplicidade e padronização

### 3. Por que Spring SIMP?

**Benefícios**:
- Abstração sobre WebSocket
- Message routing automático
- Pub/Sub nativo
- Integração com Spring Security (futura)

### 4. Por que Message Broker em Memória?

**Pros**:
- Simples, sem dependências externas
- Adequado para desenvolvimento
- Fácil de entender

**Cons**:
- Não persiste mensagens
- Não escala para múltiplos servidores
- Perde histórico ao restart

**Para Produção**:
- Usar RabbitMQ, Kafka ou Redis
- Implementar persistência
- Load balancing

### 5. Por que Records Java?

**Benefícios**:
- Imutabilidade garantida
- Menos boilerplate
- Segurança de tipo
- Melhor performance

### 6. Sanitização de HTML

**Proteção contra XSS**:
```java
HtmlUtils.htmlEscape(input.username() + ": " + input.message())
```

Converte caracteres especiais:
- `<` → `&lt;`
- `>` → `&gt;`
- `"` → `&quot;`
- `&` → `&amp;`

---

## Considerações de Segurança

### Implementado

✅ **XSS Prevention**: HTML escape de input do usuário  
✅ **Validação de Tipo**: Records garantem tipos corretos  

### Recomendado para Produção

🔲 **Autenticação**: Spring Security, JWT  
🔲 **Autorização**: Role-based access control  
🔲 **Validação**: @Valid, @NotNull, @Size  
🔲 **Rate Limiting**: Proteção contra spam  
🔲 **HTTPS/WSS**: Encriptação em trânsito  
🔲 **CORS**: Validação de origem  
🔲 **Input Sanitization**: Biblioteca jsoup  
🔲 **Logging**: Auditoria de ações  

---

## Diagrama de Classes

```
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                   │
│                  (LivechatmsApplication)                     │
└──────────────────────────┬──────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
    ┌──────────┐   ┌──────────────┐   ┌──────────────┐
    │ Config   │   │ Controller   │   │   Domain    │
    ├──────────┤   ├──────────────┤   ├──────────────┤
    │           │   │              │   │              │
    │WebSocket- │   │LiveChatCtrl  │   │ ChatInput   │
    │Config    │   │ - newMessage │   │ ChatOutput  │
    │           │   │              │   │              │
    │+ configure│   │+ message()   │   │+ record()   │
    │+ register │   │+ @Mapping()  │   │              │
    │           │   │+ @SendTo()   │   │              │
    └──────────┘   └──────────────┘   └──────────────┘
```

---

## Fluxo de Requisição HTTP vs WebSocket

### HTTP (Traditional)

```
Cliente ──► HTTP Request ──► Server
                             │ Process
                             ▼
Cliente ◄── HTTP Response ◄── Server
```

**Limitações**: Sincronous, requisição por requisição

### WebSocket (Live Chat)

```
Cliente ──► WebSocket Upgrade (HTTP Upgrade)
          │
          ├─► Conexão Persistente (TCP)
          │
          └─► Bidirecional simultâneo
              ├─ Envio de dados
              └─ Recebimento de dados
```

**Vantagens**: Asynchronous, full-duplex, baixa latência

---

## Escalabilidade Futura

### Fase 1: Atual (Simples)
- Message broker em memória
- Single server
- Sem persistência

### Fase 2: Intermediária
- RabbitMQ como broker
- Banco de dados para histórico
- JWT Authentication

### Fase 3: Produção
- Kafka para streaming
- Múltiplos servidores (load balanced)
- Cache distribuído (Redis)
- Full audit logging
- Rate limiting
- Monitoring (Prometheus)

---

## Referências Técnicas

### Spring Framework
- [Spring Boot WebSocket](https://spring.io/guides/gs/messaging-stomp-websocket/)
- [SIMP Documentation](https://docs.spring.io/spring-framework/reference/web/websocket.html)

### STOMP Protocol
- [RFC 7670 - STOMP](https://tools.ietf.org/html/rfc7670)

### JavaScript Libraries
- [StompJS Documentation](https://stomp-js.github.io/)
- [jQuery API](https://api.jquery.com/)

### Security
- [OWASP XSS Prevention](https://owasp.org/www-community/attacks/xss/)
- [Spring Security](https://spring.io/projects/spring-security)

---

**Design System v1.0** | Última atualização: Abril de 2026
