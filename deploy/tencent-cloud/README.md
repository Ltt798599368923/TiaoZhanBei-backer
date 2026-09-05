# Tencent Cloud Single-Server Deployment

This deployment runs the backend and PostgreSQL on one Tencent Cloud Lighthouse server. It is sized for the current 2-core, 2-GB server and is intended for the competition deployment.

## Before deployment

1. Keep the existing web service and its Nginx configuration running. Do not start a second Nginx container.
2. In the Lighthouse firewall, allow TCP ports `22`, `80`, and `443`.
3. Open the web terminal from the Tencent Cloud console. Do not share the server password or private key.
4. Install Docker on OpenCloudOS:

```sh
sudo dnf install -y docker docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
```

Log out and back in after the last command.

## Start the service

```sh
git clone https://github.com/Ltt798599368923/TiaoZhanBei-backer.git
cd TiaoZhanBei-backer
cp deploy/tencent-cloud/.env.example deploy/tencent-cloud/.env
chmod 600 deploy/tencent-cloud/.env
vi deploy/tencent-cloud/.env
docker compose --env-file deploy/tencent-cloud/.env -f deploy/tencent-cloud/docker-compose.yml up -d --build
```

Set the WeChat, DeepSeek, and admin values in `.env`. Use a long unique password for `POSTGRES_PASSWORD` and `ADMIN_TOKEN`.

## Verify

```sh
docker compose --env-file deploy/tencent-cloud/.env -f deploy/tencent-cloud/docker-compose.yml ps
```

The API is deliberately bound to `127.0.0.1:18080` so it cannot conflict with the existing website. Verify it with:

```sh
curl http://127.0.0.1:18080/api/ai/health
```

## Domain and HTTPS

Create an `A` record such as `api.example.com` pointing to `124.220.104.181`. After DNS takes effect, add the server block from `nginx/reverse-proxy.conf.example` to the existing Nginx configuration, replace `api.example.com` with the actual API domain, and add an HTTPS certificate. The final HTTPS domain must be added to the Mini Program request legal-domain list before changing the frontend `BASE_URL`.

## Updating manually

```sh
tar -xzf /root/tiaozhanbei-deployment.tar.gz -C /root/TiaoZhanBei-backer
cd /root/TiaoZhanBei-backer
docker compose --env-file deploy/tencent-cloud/.env -f deploy/tencent-cloud/docker-compose.yml up -d --build
```

## Automatic deployment with GitHub Actions

The server does not need outbound GitHub access. The workflow in
`.github/workflows/deploy-tencent-cloud.yml` packages the source in GitHub
Actions and uploads it over SSH whenever `main` is updated.

Create these repository Actions secrets before enabling it:

- `TENCENT_DEPLOY_HOST`: the server public IP or API host name
- `TENCENT_DEPLOY_PORT`: the SSH deployment port
- `TENCENT_DEPLOY_SSH_KEY`: a dedicated private deployment key with no passphrase

Install the corresponding public key in `/root/.ssh/authorized_keys` on the
server. The workflow deliberately excludes the server `.env` file and the
PostgreSQL/upload data directories.
