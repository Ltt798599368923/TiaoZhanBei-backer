# Tencent Cloud Single-Server Deployment

This deployment runs the backend, PostgreSQL, and Nginx on one Tencent Cloud Lighthouse server. It is sized for the current 2-core, 2-GB server and is intended for the competition deployment.

## Before deployment

1. In the Lighthouse firewall, allow TCP ports `22`, `80`, and `443`.
2. Open the web terminal from the Tencent Cloud console. Do not share the server password or private key.
3. Install Docker on OpenCloudOS:

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
curl http://127.0.0.1/api/ai/health
```

During the IP-based verification stage, use `http://124.220.104.181/api/ai/health` from a browser. Do not point the Mini Program at this IP.

## Domain and HTTPS

Create an `A` record such as `api.example.com` pointing to `124.220.104.181`. After DNS takes effect, add an HTTPS certificate and update the Nginx configuration to listen on port 443. The final HTTPS domain must be added to the Mini Program request legal-domain list before changing the frontend `BASE_URL`.

## Updating

```sh
git pull
docker compose --env-file deploy/tencent-cloud/.env -f deploy/tencent-cloud/docker-compose.yml up -d --build
```
