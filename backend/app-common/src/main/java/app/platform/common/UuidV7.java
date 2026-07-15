package app.platform.common;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

/** UUIDv7 everywhere: time-ordered, so BINARY(16) primary keys index chronologically. */
public final class UuidV7 {

    private UuidV7() {}

    public static UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /** Idempotency-Key headers must be UUIDv7 (3.13) — version nibble check. */
    public static boolean isValid(String value) {
        try {
            return UUID.fromString(value).version() == 7;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
