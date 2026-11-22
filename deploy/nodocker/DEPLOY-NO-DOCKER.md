# Deploying WITHOUT Docker (Hostinger VPS, Ubuntu 24.04)

Server IP: `201.18.205.154`  ·  Address: `https://assess.mindbridgex.in`
Layout on the server: Java runs the WAR as a system service, PostgreSQL is installed directly, nginx gives HTTPS.
Do not share passwords or the env file in chat.

## Step 1: DNS (Hostinger website)
Domains, `mindbridgex.in`, DNS records: add **A** record, Name `assess`, Points to `201.18.205.154`. Leave `@` and `www` alone.

## Step 2: Build the WAR on your Mac
In Terminal:
```
cd "/Users/deepaksingh/mindbridgex/Assestment_Platform/assessment-platform 2"
./mvnw -DskipTests clean package
```
(Needs Java 17, which you already have for STS. In STS you can instead right-click the project, Run As, Maven build, goals `clean package`, tick Skip Tests.)
The file appears at `target/assessment.war`.

## Step 3: Upload the files to the server (Mac Terminal)
```
scp target/assessment.war root@201.18.205.154:/root/app.war
scp deploy/nodocker/assessment.service deploy/nodocker/assessment.env.example deploy/nodocker/nginx-assess.conf deploy/nodocker/backup.sh root@201.18.205.154:/root/
```
Type `yes` for the fingerprint question, then the VPS root password.

## Step 4: Log in and install the software
```
ssh root@201.18.205.154
apt-get update
apt-get install -y openjdk-17-jre-headless postgresql nginx certbot python3-certbot-nginx ufw
java -version
psql --version
```
Java must say 17 and psql 16.

## Step 5: Create the database
Pick your DB password (save it in a password manager). Replace the two values in quotes first:
```
sudo -u postgres psql -c "CREATE USER deepak WITH PASSWORD 'PUT_YOUR_DB_PASSWORD_HERE';"
sudo -u postgres psql -c "CREATE DATABASE assessment OWNER deepak;"
```
(Use any user name you like, but use the same one in Step 6. The database listens only on the server itself by default, so it is not reachable from the internet.)

## Step 6: Settings file with the secrets
```
mkdir -p /etc/assessment /var/lib/assessment/uploads /opt/assessment
cp /root/assessment.env.example /etc/assessment/assessment.env
openssl rand -base64 64 | tr -d '\n'     # use as APP_JWT_SECRET
nano /etc/assessment/assessment.env
```
Replace every `CHANGE_ME`, set `DB_USERNAME` and `DB_PASSWORD` to Step 5, keep `SEED_DEMO=false`. Save: **Ctrl+O, Enter, Ctrl+X**. Then:
```
chmod 600 /etc/assessment/assessment.env
```

## Step 7: Install and start the app
```
useradd -r -s /usr/sbin/nologin assess
cp /root/app.war /opt/assessment/app.war
chown -R assess:assess /opt/assessment /var/lib/assessment
cp /root/assessment.service /etc/systemd/system/assessment.service
systemctl daemon-reload
systemctl enable --now assessment
journalctl -u assessment -f
```
Wait for `Started AssessmentApplication`, then press **Ctrl+C**. Check with `curl http://127.0.0.1:8080/actuator/health` (should show UP).

## Step 8: Firewall, web server, HTTPS
```
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
cp /root/nginx-assess.conf /etc/nginx/sites-available/assess
ln -sf /etc/nginx/sites-available/assess /etc/nginx/sites-enabled/assess
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
certbot --nginx -d assess.mindbridgex.in
```
Give your e-mail, accept the terms, and choose to redirect HTTP to HTTPS. The DNS record from Step 1 must already work (`nslookup assess.mindbridgex.in` shows the server IP). Certificates renew automatically.

## Step 9: Open the site
`https://assess.mindbridgex.in`, sign in as `superadmin`, change the password, create colleges and users.

## Step 10: Backups
```
cp /root/backup.sh /root/assess-backup.sh && chmod +x /root/assess-backup.sh
/root/assess-backup.sh
(crontab -l 2>/dev/null; echo "30 2 * * * /root/assess-backup.sh >> /root/backup.log 2>&1") | crontab -
```
Copy backups off the server regularly, and test one restore.

## Updating the app later
On your Mac repeat Step 2 and `scp target/assessment.war root@201.18.205.154:/root/app.war`. On the server, run `/root/assess-backup.sh` first, then:
```
cp /root/app.war /opt/assessment/app.war
chown assess:assess /opt/assessment/app.war
systemctl restart assessment
```

## Useful commands
- Status: `systemctl status assessment`
- Live logs: `journalctl -u assessment -f`
- Restart: `systemctl restart assessment`
- Disk: `df -h`  ·  Memory: `free -h`
