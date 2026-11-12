package personalproject.eventmgmntapp.hiveup.signup.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailRedactorTest {
    @Test
    void redactsLocalPartAndRetainsDomain() {
        assertEquals("a***@example.com", EmailRedactor.redact("alice@example.com"));
    }

    @Test
    void fullyRedactsMalformedAddresses() {
        assertEquals("***", EmailRedactor.redact("not-an-email"));
        assertEquals("***", EmailRedactor.redact("a@@example.com"));
        assertEquals("***", EmailRedactor.redact("@example.com"));
        assertEquals("***", EmailRedactor.redact("alice@"));
    }

    @Test
    void nullRemainsNull() {
        assertNull(EmailRedactor.redact(null));
    }
}
