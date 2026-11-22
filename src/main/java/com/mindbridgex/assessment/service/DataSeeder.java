package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.config.AppProperties;
import com.mindbridgex.assessment.domain.RoleType;
import com.mindbridgex.assessment.entity.User;
import com.mindbridgex.assessment.repository.UserRepository;
import com.mindbridgex.assessment.util.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

/** Creates the first administrator on an empty database. */
@Component
@Order(1)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public DataSeeder(UserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    /** Creates the Super Administrator on first start (or on upgrade), from SUPERADMIN_* settings. */
    private void ensureSuperAdmin() {
        if (users.countActiveByRole(RoleType.SUPER_ADMIN) > 0) {
            return;
        }
        AppProperties.Seed seed = props.getSeed();
        if (users.count() > 0 && (seed.getSuperPassword() == null || seed.getSuperPassword().isBlank())) {
            log.warn("No Super Administrator exists. Set SUPERADMIN_PASSWORD (and restart) to create one.");
            return;
        }
        boolean generated = seed.getSuperPassword() == null || seed.getSuperPassword().isBlank();
        String password = generated ? PasswordPolicy.generate() : seed.getSuperPassword();
        PasswordPolicy.require(password);
        if (users.existsByUsernameIgnoreCase(seed.getSuperUsername()) || users.existsByEmailIgnoreCase(seed.getSuperEmail())) {
            log.warn("Super Administrator not created: username or email already in use.");
            return;
        }
        User su = new User();
        su.setUsername(seed.getSuperUsername());
        su.setEmail(seed.getSuperEmail());
        su.setFullName("Super Administrator");
        su.setPasswordHash(encoder.encode(password));
        su.setRoles(EnumSet.of(RoleType.SUPER_ADMIN));
        su.setMustChangePassword(generated);
        users.save(su);
        if (generated) {
            log.warn("Super Administrator created. username: {}  password: {}  (change it at first sign-in)", seed.getSuperUsername(), password);
        } else {
            log.info("Super Administrator '{}' created from SUPERADMIN_USERNAME / SUPERADMIN_PASSWORD.", seed.getSuperUsername());
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existing = users.count();
        ensureSuperAdmin();
        if (existing > 0) {
            return;
        }
        AppProperties.Seed seed = props.getSeed();
        boolean generated = seed.getAdminPassword() == null || seed.getAdminPassword().isBlank();
        String password = generated ? PasswordPolicy.generate() : seed.getAdminPassword();
        PasswordPolicy.require(password);

        User admin = new User();
        admin.setUsername(seed.getAdminUsername());
        admin.setEmail(seed.getAdminEmail());
        admin.setFullName("System Administrator");
        admin.setPasswordHash(encoder.encode(password));
        admin.setRoles(EnumSet.of(RoleType.ADMIN));
        admin.setMustChangePassword(generated);
        users.save(admin);

        if (generated) {
            log.warn("==================================================================");
            log.warn(" First start: administrator account created.");
            log.warn("   username: {}", seed.getAdminUsername());
            log.warn("   password: {}   (you will be asked to change it at first sign-in)", password);
            log.warn("==================================================================");
        } else {
            log.info("Administrator '{}' created from ADMIN_USERNAME / ADMIN_PASSWORD.", seed.getAdminUsername());
        }
    }
}
