package org.sonar.sonarvalidator_backend.Config;

import org.sonar.sonarvalidator_backend.Service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserService userService;

    private final String defaultAdminPassword;

    public AdminAccountInitializer(
            UserService userService,
            @Value("${sonar.admin.password:admin}") String defaultAdminPassword) {
        this.userService = userService;
        this.defaultAdminPassword = defaultAdminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            final boolean created = userService.ensureDefaultAdmin(defaultAdminPassword);
            if (!created) {
                log.debug("admin initializer: users already exist, nothing to do");
            }
        } catch (RuntimeException ex) {
            log.error("failed to create default admin account: {}", ex.getMessage(), ex);
        }
    }
}
