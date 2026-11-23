# ClearChit deployment guide

The browser UI is packaged inside the Spring Boot JAR. Deploy one web service; you do not need to host a separate frontend or configure CORS.

## Recommended path: Render + managed PostgreSQL

The repository includes `render.yaml` as a starting point for a Render Blueprint. Review the selected service/database plans and pricing in Render before creating resources; managed database plans may incur charges.

1. Push this project to a GitHub repository you control. Do not commit `.env` files or secrets.
2. In Render, choose **New → Blueprint** and select the repository containing `render.yaml`.
3. Review the web service and PostgreSQL resource names/region and deploy the Blueprint.
4. `JWT_SECRET` is generated automatically by the Blueprint (`generateValue: true`), so each deployment gets its own unique secret and you do not need to paste one. If you prefer to set your own, use a Base64 value of at least 32 bytes, for example in PowerShell:

   ```powershell
   $bytes = New-Object byte[] 48
   [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
   [Convert]::ToBase64String($bytes)
   ```

   Never use the sample development key in production.

5. Render injects the database connection fields into the app through the Blueprint. If you changed any environment settings, save them and redeploy.
6. Open the deployed service URL. Confirm `/api/health` responds with `ClearChit backend is running`, then register a new account through the frontend.
7. Test registration, login, group creation, member enrollment, wallet demo transactions, and a cycle from the deployed site.

### Redis

The app includes Redis connection configuration, and Docker Compose provisions Redis locally. For cloud use, add a hosted Redis-compatible service if you enable/use Redis-backed features. Set `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` (if provided), and `REDIS_SSL=true` when the provider requires TLS. Do not paste credentials into source files. The current UI and core workflow do not require a separate frontend host.

## Alternative: deploy the Docker image yourself

Build and run the included `Dockerfile` on any container host that supports environment variables. Provide a reachable PostgreSQL database and set:

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `PORT` to the port expected by the platform
- `JWT_SECRET` to the unique Base64 secret described above
- Redis settings if you use Redis-backed features

Use a persistent/managed database for production; do not rely on the ephemeral filesystem of a web container for database storage.

## Deployment safety checklist

- [ ] Set a unique production `JWT_SECRET` and keep it private.
- [ ] Use managed PostgreSQL with backups and restricted credentials.
- [ ] Keep `SHOW_SQL=false` and use `DDL_AUTO=validate` or a migration tool after your schema is finalized. `update` is a convenience for development, not a substitute for migrations.
- [ ] Do not put actual financial balances or payments into this demo without security, authorization, compliance, and audit review.
- [ ] Verify access control for every API operation before making the app public. Authentication is enabled, but a production review should confirm object-level authorization for every group, member, wallet, cycle, and ledger request.
- [ ] Rotate any credentials or JWTs that were shared in logs, screenshots, or chat.
