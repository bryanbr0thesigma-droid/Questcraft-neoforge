package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Each installer only knows its own spelling of the option, an unknown one makes it print a stack trace and quit. */
class InstallerArgumentTest {
    @Test
    void forgesInstallerNeedsItsOwnSpelling() {
        assertEquals("--installClient", ForgeInstaller.INSTALL_ARG);
    }
}
