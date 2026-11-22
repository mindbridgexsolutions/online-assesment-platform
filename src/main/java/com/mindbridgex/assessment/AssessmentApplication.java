package com.mindbridgex.assessment;

import com.mindbridgex.assessment.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(AppProperties.class)
public class AssessmentApplication extends SpringBootServletInitializer {

    static {
        // The JDBC driver sends the JVM time zone to PostgreSQL, which rejects legacy aliases such as
        // "Asia/Calcutta" on some installs. Run the JVM in UTC; display uses app.timezone (see Fmt).
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(AssessmentApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(AssessmentApplication.class, args);
    }
}
