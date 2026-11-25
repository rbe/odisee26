package org.odisee.document

// id: ADR-0007

import groovy.test.GroovyTestCase

/**
 * Retention lives in {@code .claude/skills/github-pipeline/sdlc.mk}. APM
 * installs that file from rbe/ralfskills, and {@code apm.lock.yaml} pins it.
 * A publish keeps this run and the next-lower run, and it does not delete
 * a version that still wears {@code latest} or any tag that is not
 * {@code MAJOR.MINOR.PATCH-<run>}.
 */
class PublishedVersionsTest extends GroovyTestCase {

    void testSdlcMkIsTheInstalledSkillFile() {
        String lock = new File(repoRoot(), 'apm.lock.yaml').text
        assertTrue(lock.contains('repo_url: rbe/ralfskills'))
        assertTrue(lock.contains('resolved_commit: '))
        assertTrue(lock.contains('.claude/skills/github-pipeline/sdlc.mk'))
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
        new File(repoRoot(), '.claude/skills/github-pipeline/sdlc.mk').text
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
