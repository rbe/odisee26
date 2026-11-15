package org.odisee.document

import groovy.test.GroovyTestCase

import java.security.MessageDigest

/**
 * Retention lives in {@code sdlc.mk}. The file is the canonical copy.
 * A publish keeps this run and the next-lower run, and it does not delete
 * a version that still wears {@code latest} or any tag that is not
 * {@code MAJOR.MINOR.PATCH-<run>}.
 */
class PublishedVersionsTest extends GroovyTestCase {

    private static final String SDLC_SHA256 = '45c6cf23664d030c61f0fedd2d0639cd9dc2dc7bb03c14167dce61fed9d495d6'

    void testSdlcMkIsTheCanonicalCopy() {
        byte[] bytes = new File(repoRoot(), 'sdlc.mk').bytes
        String hash = MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
        assertEquals(SDLC_SHA256, hash)
        assertFalse(new File(repoRoot(), '.github/scripts/published-versions.sh').exists())
    }

    void testOffDefaultBranchDeletesNothing() {
        String sdlc = sdlc()
        assertTrue(sdlc.contains('Ref is not the default branch. Tagged the version only. Nothing deleted.'))
        assertTrue(sdlc.contains('else on_default=0'))
    }

    void testMissingPackageIsAnEmptyList() {
        String sdlc = sdlc()
        assertTrue(sdlc.contains('if grep -q 404'))
        assertTrue(sdlc.contains('A missing GHCR package (HTTP 404) is an empty list.'))
    }

    void testLatestAndNonRollingTagsStay() {
        String sdlc = sdlc()
        assertTrue(sdlc.contains('if (!rolling(p)) protected[n] = 1'))
        assertTrue(sdlc.contains('Versions that wear latest or a non-rolling tag were left in place.'))
        assertTrue(sdlc.contains('case "$$name" in v*)'))
    }

    void testFormerIsTheNextLowerRunNumber() {
        String sdlc = sdlc()
        assertTrue(sdlc.contains('keep this run and the next-lower run'))
        assertTrue(sdlc.contains('if (best < 0 || r > best) best = r'))
    }

    private static String sdlc() {
        new File(repoRoot(), 'sdlc.mk').text
    }

    private static File repoRoot() {
        File dir = new File(System.getProperty('user.dir'))
        while (dir != null && !new File(dir, 'settings.gradle.kts').isFile()) {
            dir = dir.parentFile
        }
        assertNotNull(dir)
        dir
    }
}
