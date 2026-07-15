package app.platform.api.arch;

import static org.assertj.core.api.Assertions.assertThat;

import app.platform.security.LocalDevSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

/**
 * The dev-header auth bypass must never ride outside the `local` profile (8.06 enhancement) —
 * dev bypasses have a way of shipping.
 */
class LocalDevBypassTest {

    @Test
    void devBypassIsLocalProfileOnly() {
        Profile profile = LocalDevSecurityConfig.class.getAnnotation(Profile.class);
        assertThat(profile).as("@Profile annotation present").isNotNull();
        assertThat(profile.value()).containsExactly("local");
    }
}
