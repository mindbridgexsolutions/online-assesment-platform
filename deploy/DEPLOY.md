# Deploying the Online Assessment Platform on a Hostinger VPS

Server IP and domain used below (change them if yours differ):
- VPS IP: `201.18.205.154`
- Address students will use: `https://assess.mindbridgex.in`

You will work in three places: the **Hostinger website** (DNS), the **Terminal app on your Mac**, and the **server** (through Terminal).
Never paste passwords or the `.env` file into chat.

---

## Step 1: Point the sub-domain to the server (Hostinger website)
1. Log in to hpanel.hostinger.com, open **Domains**, click `mindbridgex.in`, then **DNS / Nameservers**.
2. Add a record: **Type** `A`, **Name** `assess`, **Points to** `201.18.205.154`, **TTL** default. Save.
3. Do not change the existing `@` and `www` records.
4. Check it (in Mac Terminal, may take a few minutes): `nslookup assess.mindbridgex.in` should show `201.18.205.154`.

## Step 2: Copy the project to the server (Mac Terminal)
1. Open **Terminal** (Cmd + Space, type Terminal).
2. Make a zip without build files:
   ```
   cd "/Users/deepaksingh/mindbridgex/Assestment_Platform"
   zip -r assess.zip "assessment-platform 2" -x "*/target/*" "*/.git/*" "*/data/*"
   ```
3. Upload it (type `yes` if asked about the fingerprint, then the server password you set when buying):
   ```
   scp assess.zip root@201.18.205.154:/root/
   ```

## Step 3: Log in to the server (Mac Terminal)
```
ssh root@201.18.205.154
```
Enter the VPS root password. The prompt changes to `root@...#`. Everything below is typed here.

## Step 4: Check Docker, set up the firewall
```
docker --version
docker compose version
```
If both print a version, go on. If "command not found":
```
curl -fsSL https://get.docker.com | sh
```
Firewall (open only SSH, web):
```
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
```

## Step 5: Unpack the project
```
apt-get install -y unzip
cd /root && unzip -o assess.zip
mv "assessment-platform 2" assessment-platform
cd assessment-platform/deploy
```

## Step 6: Create the secret settings file
```
cp .env.example .env
```
Generate three random secrets and **write them down in a password manager**:
```
openssl rand -base64 36    # use as DB_PASSWORD
openssl rand -base64 64    # use as APP_JWT_SECRET (remove line breaks)
openssl rand -base64 18    # use for ADMIN_PASSWORD, and another for SUPERADMIN_PASSWORD
```
Edit the file: `nano .env` , replace every `CHANGE_ME`, set your real e-mail, keep `SEED_DEMO=false`.
Save with **Ctrl+O, Enter**, exit with **Ctrl+X**. Protect it: `chmod 600 .env`

## Step 7: Start everything
```
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```
The first build takes about 5 to 10 minutes. Then:
```
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f app
```
Wait for the line `Started AssessmentApplication`, then press **Ctrl+C** (the app keeps running).
Caddy fetches the HTTPS certificate on its own (the DNS record from Step 1 must already work).

## Step 8: Open the site
Browse to `https://assess.mindbridgex.in` and sign in as `superadmin` with the password you set.
Immediately: change the password under your profile, then create the college, teachers and students (or use Excel import).

## Step 9: Daily backups
```
cd /root/assessment-platform/deploy
./backup.sh
(crontab -l 2>/dev/null; echo "30 2 * * * /root/assessment-platform/deploy/backup.sh >> /root/backup.log 2>&1") | crontab -
```
Backups are saved in `/root/backups`. Copy them off the server regularly (Hostinger's VPS backup option or `scp` to your Mac). A backup you have never restored is not proven.

## Step 10: Updating the app later
Upload a new zip as in Step 2, unpack over the folder, then in `deploy`:
```
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```
Database tables update automatically. Run `./backup.sh` first.

## Useful commands
- Status: `docker compose -f docker-compose.prod.yml ps`
- Live logs: `docker compose -f docker-compose.prod.yml logs -f app`
- Restart the app: `docker compose -f docker-compose.prod.yml restart app`
- Disk space: `df -h`  ·  Memory: `free -h`

## Before real students use it
1. Run one full mock exam with several staff on the real URL.
2. Do a restore test of a backup on a spare folder or machine.
3. Keep the first live exam small (one class). Load for hundreds of students is untested.
4. Add two-factor login and an independent security test before high-stakes exams.
