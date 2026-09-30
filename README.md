# Chat Engine

Spring Boot MVC/STOMP chat service with a vanilla JavaScript client and MySQL persistence.

## Architecture

- **HTTP API:** stateless Spring Security filter chain; short-lived HS256 JWTs authenticate accounts and API requests.
- **WebSocket:** `/ws` uses STOMP. A bearer token is validated on the STOMP `CONNECT` frame before subscriptions or application messages are accepted. Browsers cannot set arbitrary HTTP upgrade headers, so the token is sent in the STOMP CONNECT headers instead of a URL query string.
- **Authorization:** every room subscription and chat action checks persisted room membership; user IDs and sender names come from the authenticated principal, not the message body.
- **Persistence:** MySQL stores users, rooms, memberships, messages, idempotency keys, receipts, and an outbox. Flyway owns the schema.
- **Room discovery:** authenticated users can create invite-link rooms, join by room ID, and browse rooms they have not joined.
- **Room lifecycle and moderation:** room owners can switch visibility, rotate private invite tokens, kick or ban members, and delete rooms with their messages and receipts. Members can leave rooms.
- **Message controls:** authors can edit or delete their own messages. Delivery and read receipts are persisted for room recipients and reflected in the sender's message history.
- **Events and recovery:** committed messages and outbox rows are saved in one SQL transaction. The outbox publisher forwards committed events to the local STOMP broker, and clients ACK only after database persistence. The database-backed history API recovers messages after reconnect.
- **Presence and rate limits:** active-room session presence and rate-limit windows are held in application memory; STOMP protocol heartbeats detect dead sockets.
- **Frontend:** Vanilla HTML, CSS, and JavaScript + Vite + STOMP.js. The client handles room membership, history, ACKs, typing/presence, read receipts, safe text rendering, and jittered exponential reconnection.

## Local development

Requirements: Java 17+, Maven, Node.js 20+, npm, and Docker Compose.

1. Start MySQL:

   ```powershell
   docker compose up -d mysql
   ```

2. In one terminal, start the backend:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   Flyway creates the schema at startup. The local connection defaults are in `application.properties`.

3. In a second terminal, start the frontend development server:

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

   Open <http://localhost:5173>. Vite proxies `/api` and `/ws` to Spring Boot on port 8080.

4. To serve the built frontend from Spring Boot instead:

   ```powershell
   cd frontend
   npm run build
   cd ..
   .\mvnw.cmd spring-boot:run
   ```

   Vite writes the production bundle into `src/main/resources/static`. The Dockerfile performs this frontend build automatically.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `MYSQL_URL` | `jdbc:mysql://localhost:3306/chat_engine?...` | JDBC connection |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `chat` / `chat` | Database credentials |
| `DB_POOL_SIZE` | `30` | Per-instance Hikari maximum |
| `JWT_SECRET` | local-development-only value | HS256 key; configure a unique random secret of at least 32 bytes in deployment |
| `CHAT_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:8080` | Exact allowed browser origins |
| `CHAT_MESSAGES_PER_MINUTE` | `30` | Per-user send limit |
| `CHAT_TYPING_PER_MINUTE` | `60` | Per-user typing-event limit |
| `CHAT_RECEIPTS_PER_MINUTE` | `300` | Per-user receipt-event limit |
| `CHAT_ROOM_JOINS_PER_MINUTE` | `30` | Per-user room-join-event limit |
| `CHAT_HANDSHAKES_PER_MINUTE` | `20` | Per-IP auth/WebSocket entry limit |

Do not use the built-in JWT secret, local database credentials, or permissive development origins in production. Use TLS for HTTP/WebSocket traffic and provide secrets through a managed secret store. Configure trusted proxy handling before relying on forwarded client IPs for IP rate limiting.

## HTTP API

- `POST /api/auth/register` — `{ "email", "displayName", "password" }`
- `POST /api/auth/login` — `{ "email", "password" }`
- `GET /api/rooms` — rooms for the authenticated account
- `GET /api/rooms/public` — rooms available to join
- `POST /api/rooms` — `{ "name" }`; creator becomes `OWNER`
- `POST /api/rooms/{roomId}/join?invite={inviteCode}` — join a public room or a private room with its invite token
- `DELETE /api/rooms/{roomId}/memberships/current` — leave a room; owners may rejoin with their owner permissions
- `DELETE /api/rooms/{roomId}` — owner-only room deletion, including chat history and receipts
- `PATCH /api/rooms/{roomId}/settings` — `{ "isPublic": true|false }`; owner only
- `POST /api/rooms/{roomId}/invite/rotate` — invalidate the previous invite token; owner only
- `DELETE /api/rooms/{roomId}/members/{memberId}` — owner-only kick
- `POST /api/rooms/{roomId}/members/{memberId}/ban` — owner-only ban
- `GET /api/rooms/{roomId}/messages?limit=50&before=...&beforeId=...` — authorized cursor history

Protected HTTP calls use `Authorization: Bearer <accessToken>`.

## STOMP protocol

1. Open WebSocket `/ws`.
2. Send STOMP `CONNECT` with native header `Authorization: Bearer <accessToken>`.
3. Subscribe to `/topic/rooms/{roomId}` and `/user/queue/events`. The room membership must already exist.
4. Send messages to `/app/chat.send`:

   ```json
   {
     "type": "CHAT",
     "roomId": "room-uuid",
     "clientMessageId": "client-generated-uuid",
     "content": "Hello"
   }
   ```

   `clientMessageId` makes retries idempotent. The authenticated principal determines the sender. The server persists the message and receipt rows, writes the outbox record in the same transaction, and replies on the user's queue with an `ACK`.

- `/app/chat.typing` — `{ "roomId": "room-uuid" }`
- `/app/room.join` — `{ "roomId": "room-uuid" }`; broadcasts an online-members snapshot
- `/app/heartbeat` — `{}`; refreshes the local presence lease
- `/app/chat.receipt` — `{ "messageId": "message-uuid", "state": "DELIVERED" | "READ" }`
- `/app/chat.edit` — `{ "messageId": "message-uuid", "content": "Updated message" }`; authors may edit their own messages
- `/app/chat.delete` — `{ "messageId": "message-uuid" }`; authors may delete their own messages

## Scaling and guarantees

The WebSocket server does not use a pool of socket objects: sockets are long-lived sessions. Real-time fan-out, presence, and rate limits are local to one application instance, so run a single instance unless shared coordination is added. Use bounded STOMP executors and a bounded Hikari pool, and size the JDBC pool below the MySQL connection budget.

The accepted-message guarantee is **durable database commit before ACK**. The outbox retries local event publication, and clients recover missed live events from MySQL history when reconnecting.

Before production, add refresh-token rotation/revocation, email verification/password recovery, administrative room moderation, MySQL TLS and HA, metrics/tracing/alerts, retention/partitioning policies, and load tests for connection churn and slow clients.
