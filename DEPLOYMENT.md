# Deployment Configuration

The backend requires Java 17 and PostgreSQL.

## Required production environment variables

| Variable | Purpose |
| --- | --- |
| `DB_HOST` | PostgreSQL host name. |
| `DB_PORT` | PostgreSQL port, typically `5432`. |
| `DB_NAME` | PostgreSQL database name. |
| `DB_USERNAME` | PostgreSQL user name. |
| `DB_PASSWORD` | PostgreSQL password. |
| `DEEPSEEK_API_KEY` | DeepSeek API key for chat and legal search. |
| `ADMIN_TOKEN` | A strong, private token for `/api/admin` operations. |
| `WECHAT_LOGIN_ENABLED` | Set to `true` for real Mini Program login. |
| `WECHAT_APP_ID` | Mini Program AppID when real login is enabled. |
| `WECHAT_APP_SECRET` | Mini Program AppSecret when real login is enabled. |

`WECHAT_LOGIN_ENABLED=false` is a development-only fallback. It creates a stable local identity from the nickname and must not be used in a public deployment.

## File storage

Contract uploads are stored under `FILE_UPLOAD_DIR`, which defaults to `./uploads`. Render local disks are ephemeral, so production deployments should set this to durable object storage through a future storage adapter before accepting real user documents.

## Local verification

Use JDK 17 or newer, then run:

```powershell
mvn test
```

The default Java runtime configured on this workstation is Java 8, so the JDK 17 installation at `D:\daimaruanjian\JDK17` must be selected for local backend builds.
