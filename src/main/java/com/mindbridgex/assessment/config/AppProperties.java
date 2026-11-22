package com.mindbridgex.assessment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String timezone = "Asia/Kolkata";
    private Jwt jwt = new Jwt();
    private Cookie cookie = new Cookie();
    private Attempts attempts = new Attempts();
    private Storage storage = new Storage();
    private Login login = new Login();
    private Seed seed = new Seed();

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }


    public static class Jwt {
        private String secret;
        private String issuer = "mindbridgex-assessment";
        private int accessTtlMinutes = 20;
        private int refreshTtlDays = 7;

        public String getSecret() {
            return this.secret;
        }

        public String getIssuer() {
            return this.issuer;
        }

        public int getAccessTtlMinutes() {
            return this.accessTtlMinutes;
        }

        public int getRefreshTtlDays() {
            return this.refreshTtlDays;
        }

        public void setSecret(final String secret) {
            this.secret = secret;
        }

        public void setIssuer(final String issuer) {
            this.issuer = issuer;
        }

        public void setAccessTtlMinutes(final int accessTtlMinutes) {
            this.accessTtlMinutes = accessTtlMinutes;
        }

        public void setRefreshTtlDays(final int refreshTtlDays) {
            this.refreshTtlDays = refreshTtlDays;
        }
    }


    public static class Cookie {
        private boolean secure = true;
        private String sameSite = "Strict";

        public boolean isSecure() {
            return this.secure;
        }

        public String getSameSite() {
            return this.sameSite;
        }

        public void setSecure(final boolean secure) {
            this.secure = secure;
        }

        public void setSameSite(final String sameSite) {
            this.sameSite = sameSite;
        }
    }


    public static class Attempts {
        private int graceSeconds = 10;
        private long autoSubmitIntervalMs = 15000;
        private int maxAutosubmitBatch = 500;

        public int getGraceSeconds() {
            return this.graceSeconds;
        }

        public long getAutoSubmitIntervalMs() {
            return this.autoSubmitIntervalMs;
        }

        public int getMaxAutosubmitBatch() {
            return this.maxAutosubmitBatch;
        }

        public void setGraceSeconds(final int graceSeconds) {
            this.graceSeconds = graceSeconds;
        }

        public void setAutoSubmitIntervalMs(final long autoSubmitIntervalMs) {
            this.autoSubmitIntervalMs = autoSubmitIntervalMs;
        }

        public void setMaxAutosubmitBatch(final int maxAutosubmitBatch) {
            this.maxAutosubmitBatch = maxAutosubmitBatch;
        }
    }


    public static class Storage {
        private String dir = "./data/uploads";
        private long maxImageBytes = 2 * 1024 * 1024;
        private long maxDocumentBytes = 10 * 1024 * 1024;
        private int maxImageDimension = 6000;

        public int getMaxImageDimension() {
            return this.maxImageDimension;
        }

        public void setMaxImageDimension(final int maxImageDimension) {
            this.maxImageDimension = maxImageDimension;
        }

        public String getDir() {
            return this.dir;
        }

        public long getMaxImageBytes() {
            return this.maxImageBytes;
        }

        public long getMaxDocumentBytes() {
            return this.maxDocumentBytes;
        }

        public void setDir(final String dir) {
            this.dir = dir;
        }

        public void setMaxImageBytes(final long maxImageBytes) {
            this.maxImageBytes = maxImageBytes;
        }

        public void setMaxDocumentBytes(final long maxDocumentBytes) {
            this.maxDocumentBytes = maxDocumentBytes;
        }
    }


    public static class Login {
        private int maxFailedAttempts = 5;
        private int lockMinutes = 15;
        private int windowMinutes = 15;
        private int maxPerIp = 1000;
        private int maxPerUser = 10;

        public int getMaxFailedAttempts() {
            return this.maxFailedAttempts;
        }

        public int getLockMinutes() {
            return this.lockMinutes;
        }

        public int getWindowMinutes() {
            return this.windowMinutes;
        }

        public int getMaxPerIp() {
            return this.maxPerIp;
        }

        public int getMaxPerUser() {
            return this.maxPerUser;
        }

        public void setMaxFailedAttempts(final int maxFailedAttempts) {
            this.maxFailedAttempts = maxFailedAttempts;
        }

        public void setLockMinutes(final int lockMinutes) {
            this.lockMinutes = lockMinutes;
        }

        public void setWindowMinutes(final int windowMinutes) {
            this.windowMinutes = windowMinutes;
        }

        public void setMaxPerIp(final int maxPerIp) {
            this.maxPerIp = maxPerIp;
        }

        public void setMaxPerUser(final int maxPerUser) {
            this.maxPerUser = maxPerUser;
        }
    }


    public static class Seed {
        private String adminUsername = "admin";
        private String adminEmail = "admin@example.com";
        private String adminPassword;
        private String superUsername = "superadmin";
        private String superEmail = "superadmin@example.com";
        private String superPassword;
        private boolean demoData = false;

        public String getSuperUsername() {
            return this.superUsername;
        }

        public void setSuperUsername(final String v) {
            this.superUsername = v;
        }

        public String getSuperEmail() {
            return this.superEmail;
        }

        public void setSuperEmail(final String v) {
            this.superEmail = v;
        }

        public String getSuperPassword() {
            return this.superPassword;
        }

        public void setSuperPassword(final String v) {
            this.superPassword = v;
        }

        public String getAdminUsername() {
            return this.adminUsername;
        }

        public String getAdminEmail() {
            return this.adminEmail;
        }

        public String getAdminPassword() {
            return this.adminPassword;
        }

        public boolean isDemoData() {
            return this.demoData;
        }

        public void setAdminUsername(final String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public void setAdminEmail(final String adminEmail) {
            this.adminEmail = adminEmail;
        }

        public void setAdminPassword(final String adminPassword) {
            this.adminPassword = adminPassword;
        }

        public void setDemoData(final boolean demoData) {
            this.demoData = demoData;
        }
    }

    public String getTimezone() {
        return this.timezone;
    }

    public Jwt getJwt() {
        return this.jwt;
    }

    public Cookie getCookie() {
        return this.cookie;
    }

    public Attempts getAttempts() {
        return this.attempts;
    }

    public Storage getStorage() {
        return this.storage;
    }

    public Login getLogin() {
        return this.login;
    }

    public Seed getSeed() {
        return this.seed;
    }

    public void setTimezone(final String timezone) {
        this.timezone = timezone;
    }

    public void setJwt(final Jwt jwt) {
        this.jwt = jwt;
    }

    public void setCookie(final Cookie cookie) {
        this.cookie = cookie;
    }

    public void setAttempts(final Attempts attempts) {
        this.attempts = attempts;
    }

    public void setStorage(final Storage storage) {
        this.storage = storage;
    }

    public void setLogin(final Login login) {
        this.login = login;
    }

    public void setSeed(final Seed seed) {
        this.seed = seed;
    }
}
