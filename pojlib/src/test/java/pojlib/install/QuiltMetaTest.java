package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class QuiltMetaTest {
    private static QuiltMeta.QuiltVersion[] versions(String... names) {
        QuiltMeta.QuiltVersion[] all = new QuiltMeta.QuiltVersion[names.length];
        for (int i = 0; i < names.length; i++) {
            all[i] = new QuiltMeta.QuiltVersion();
            all[i].version = names[i];
        }
        return all;
    }

    @Test
    void theNewestStableLoaderIsPreferredOverNewerPreReleases() {
        // the order of Quilt's meta: newest first, betas in between
        assertEquals("0.30.1", QuiltMeta.selectLatest(
                versions("0.31.0-beta.4", "0.31.0-beta.1", "0.30.2-beta.1", "0.30.1", "0.30.1-beta.4", "0.29.2")).version);
    }

    @Test
    void withoutAStableLoaderThePreReleaseIsUsed() {
        assertEquals("0.31.0-beta.4", QuiltMeta.selectLatest(versions("0.31.0-beta.4", "0.31.0-beta.3")).version);
    }

    @Test
    void noLoadersMeansNoChoice() {
        assertNull(QuiltMeta.selectLatest(versions()));
    }
}
