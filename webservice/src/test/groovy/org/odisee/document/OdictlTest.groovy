package org.odisee.document

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.test.GroovyTestCase
import org.odisee.io.OdiseePath
import org.odisee.io.TenantPaths
import org.odisee.security.OidcFile

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

/**
 * {@code odictl} writes the files the server reads.
 * The script runs from the source tree. It does not start a container.
 */
class OdictlTest extends GroovyTestCase {

    private HttpServer keycloak

    private String issuer

    private final List<String> createdClients = Collections.synchronizedList([])

    @Override
    protected void setUp() {
        Files.deleteIfExists(OidcFile.location())
        Files.deleteIfExists(CallbackAllowList.location())
        Files.deleteIfExists(BucketFile.location())
        deleteUser('ctlada')
        deleteUser('ctlbucket')
        createdClients.clear()
        keycloak = fakeKeycloak(createdClients)
        issuer = "http://127.0.0.1:${keycloak.address.port}/realms/odisee"
    }

    @Override
    protected void tearDown() {
        keycloak?.stop(0)
        Files.deleteIfExists(OidcFile.location())
        Files.deleteIfExists(CallbackAllowList.location())
        Files.deleteIfExists(BucketFile.location())
        deleteUser('ctlada')
        deleteUser('ctlbucket')
    }

    void testUserCreatesTheClientDirectoriesAndPrintsTheSecret() {
        String output = succeed('user', 'ctlada')

        assertTrue(output.contains("User 'ctlada' created."))
        assertTrue(output.contains('client_id=ctlada'))
        String secret = output.readLines().find { it.startsWith('client_secret=') }?.substring('client_secret='.length())
        assertNotNull(secret)
        assertTrue(secret.length() >= 32)
        assertFalse(Files.exists(OdiseePath.ODISEE_HOME.resolve('etc').resolve('users')))
        assertTrue(Files.isDirectory(TenantPaths.templateDir('ctlada')))
        assertTrue(Files.isDirectory(TenantPaths.workDir('ctlada')))
        assertTrue(Files.isDirectory(TenantPaths.outputDir('ctlada')))
        OidcFile.Record record = OidcFile.read(OidcFile.location())
        assertEquals(issuer, record.issuer)
        assertEquals('odisee', record.audience)
        assertEquals('odisee-ui', record.uiClient)
        String oidc = Files.readString(OidcFile.location())
        assertFalse(oidc.contains(secret))
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(OidcFile.location())
        assertFalse(perms.contains(PosixFilePermission.GROUP_READ))
        assertFalse(perms.contains(PosixFilePermission.OTHERS_READ))
    }

    void testBadUserNameFailsAndCreatesNothing() {
        String output = denied('user', '../ctlada')

        assertTrue(output.contains('Invalid user'))
        assertFalse(Files.exists(OidcFile.location()))
        assertFalse(Files.exists(TenantPaths.userRoot('ctlada')))
    }

    void testExistingUserFails() {
        succeed('user', 'ctlada')

        String output = denied('user', 'ctlada')

        assertTrue(output.contains("User 'ctlada' already exists"))
        assertTrue(Files.isDirectory(TenantPaths.templateDir('ctlada')))
    }

    void testUserTakesANameOnly() {
        String missing = denied('user')
        String extra = denied('user', 'ctlada', 's3cret-value')
        String reserved = denied('user', 'odisee-ui')

        assertTrue(missing.contains('usage:'))
        assertTrue(extra.contains('usage:'))
        assertTrue(reserved.contains('Invalid user'))
        assertFalse(Files.exists(TenantPaths.userRoot('ctlada')))
        assertFalse(Files.exists(TenantPaths.userRoot('odisee-ui')))
    }

    void testUserWithoutAdminCredentialsFails() {
        String output = run(false, ['ODISEE_KEYCLOAK_ADMIN': '', 'ODISEE_KEYCLOAK_ADMIN_PASSWORD': ''], 'user', 'ctlada')

        assertTrue(output.contains('Keycloak admin credentials are required'))
        assertFalse(Files.exists(TenantPaths.userRoot('ctlada')))
    }

    void testCallbackHostAppendsAHostTheServerReads() {
        succeed('callback-host', 'ctlada', 'hooks.example')
        succeed('callback-host', 'ctlada', 'Hooks.Example')
        succeed('callback-host', 'ctlbucket', 'other.example')

        assertTrue(CallbackAllowList.permits('ctlada', 'https://hooks.example/done'))
        assertFalse(CallbackAllowList.permits('ctlada', 'https://other.example/done'))
        assertTrue(CallbackAllowList.permits('ctlbucket', 'https://other.example/done'))
        assertFalse(CallbackAllowList.permits('ctlbucket', 'https://hooks.example/done'))
        List<String> lines = Files.readAllLines(CallbackAllowList.location()).findAll { it.trim() && !it.trim().startsWith('#') }
        assertEquals(['ctlada hooks.example', 'ctlbucket other.example'], lines)
    }

    void testBadCallbackHostFails() {
        String output = denied('callback-host', 'ctlada', 'https://hooks.example/done')
        String badUser = denied('callback-host', '../ctlada', 'hooks.example')

        assertTrue(output.contains('Invalid callback host'))
        assertTrue(badUser.contains('Invalid user'))
        assertFalse(Files.exists(CallbackAllowList.location()))
        assertFalse(CallbackAllowList.permits('ctlada', 'https://hooks.example/done'))
    }

    void testBucketWritesTheLineTheServerReads() {
        succeed('bucket', 'ctlbucket', 'http://minio:9000', 'us-east-1', 'odisee-files', 'minio', 'minio secret')

        BucketFile.Record record = BucketFile.require('ctlbucket')
        assertEquals('http://minio:9000', record.endpoint)
        assertEquals('us-east-1', record.region)
        assertEquals('odisee-files', record.bucket)
        assertEquals('minio', record.accessKey)
        assertEquals('minio secret', record.secret)
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(BucketFile.location())
        assertFalse(perms.contains(PosixFilePermission.GROUP_READ))
        assertFalse(perms.contains(PosixFilePermission.OTHERS_READ))

        succeed('bucket', 'ctlbucket', 'http://127.0.0.1:9000', 'us-east-1', 'other-files', 'minio', 'next secret')
        BucketFile.Record replaced = BucketFile.require('ctlbucket')
        assertEquals('other-files', replaced.bucket)
        assertEquals('next secret', replaced.secret)
        assertEquals('http://127.0.0.1:9000', replaced.endpoint)
    }

    void testBadBucketFails() {
        String output = denied('bucket', 'ctlbucket', 'ftp://minio:9000', 'us-east-1', 'odisee-files', 'minio', 'minio-secret')

        assertTrue(output.contains('Invalid endpoint'))
        assertFalse(Files.exists(BucketFile.location()))
    }

    void testUnsetHomeUsesTheUserShareDirectory() {
        Path tree = Files.createTempDirectory('odictl-home')
        try {
            Path home = tree.resolve('home')
            Files.createDirectories(home)
            String output = launch(true, [scriptPath(), 'callback-host', 'ctlpath', 'hooks.example'], [
                    HOME: home.toString(),
                    ODISEE_HOME: '',
                    JAVA_HOME: '',
                    PATH: '/usr/bin:/bin',
            ])

            assertTrue(output.contains("Callback host 'hooks.example' listed for 'ctlpath'."))
            Path root = home.resolve('.local/share/odisee')
            assertTrue(Files.readString(root.resolve('etc/callback-hosts')).contains('ctlpath hooks.example'))
            assertFalse(Files.exists(root.resolve('.fonts')))
            assertFalse(Files.exists(home.resolve('.bash_profile')))
            assertFalse(Files.exists(root.resolve('etc/odienv')))
        } finally {
            PathTree.delete(tree)
        }
    }

    void testSetHomeWinsAndDoesNotNeedJavaHome() {
        Path tree = Files.createTempDirectory('odictl-set-home')
        try {
            Path home = tree.resolve('home')
            Path install = tree.resolve('opt')
            Path shim = tree.resolve('shim')
            Files.createDirectories(home)
            Files.createDirectories(install)
            Files.createDirectories(shim)
            Files.writeString(shim.resolve('java'), '#!/bin/sh\nexit 1\n')
            Files.setPosixFilePermissions(shim.resolve('java'), PosixFilePermissions.fromString('rwxr-xr-x'))
            String output = launch(true, [scriptPath(), 'callback-host', 'ctlhome', 'hooks.example'], [
                    HOME: home.toString(),
                    ODISEE_HOME: install.toString(),
                    JAVA_HOME: '',
                    PATH: "${shim}:/usr/bin:/bin",
            ])

            assertTrue(output.contains("Callback host 'hooks.example' listed for 'ctlhome'."))
            assertTrue(Files.isRegularFile(install.resolve('etc/callback-hosts')))
            assertFalse(Files.exists(home.resolve('.local/share/odisee')))
            assertFalse(Files.exists(install.resolve('.fonts')))
        } finally {
            PathTree.delete(tree)
        }
    }

    void testLinkedCommandUsesTheDefaultHome() {
        Path tree = Files.createTempDirectory('odictl-link')
        try {
            Path home = tree.resolve('home')
            Path bindir = tree.resolve('bin')
            Files.createDirectories(home)
            Files.createDirectories(bindir)
            Files.createSymbolicLink(bindir.resolve('odictl'), Path.of(scriptPath()))
            String output = launch(true, ['/usr/bin/env', 'odictl', 'callback-host', 'ctllink', 'hooks.example'], [
                    HOME: home.toString(),
                    ODISEE_HOME: '',
                    JAVA_HOME: '',
                    PATH: "${bindir}:/usr/bin:/bin",
            ])

            assertTrue(output, output.contains("Callback host 'hooks.example' listed for 'ctllink'."))
            assertTrue(Files.readString(home.resolve('.local/share/odisee/etc/callback-hosts')).contains('ctllink hooks.example'))
        } finally {
            PathTree.delete(tree)
        }
    }

    void testJavaFromPathIsNotExported() {
        Path tree = Files.createTempDirectory('odictl-java')
        Path install = tree.resolve('odisee')
        try {
            Path home = tree.resolve('home')
            Path javaBin = tree.resolve('jdk/bin')
            Files.createDirectories(home)
            Files.createDirectories(install.resolve('etc'))
            Files.createDirectories(javaBin)
            Files.writeString(install.resolve('application.jar'), '')
            Path argsFile = tree.resolve('args.txt')
            Path envFile = tree.resolve('env.txt')
            Files.writeString(javaBin.resolve('java'), "#!/bin/sh\nprintf '%s\\n' \"\$*\" > '${argsFile}'\nenv > '${envFile}'\n")
            Files.setPosixFilePermissions(javaBin.resolve('java'), PosixFilePermissions.fromString('rwxr-xr-x'))
            String output = launch(true, [scriptPath(), '-q', 'start'], [
                    HOME: home.toString(),
                    ODISEE_HOME: install.toString(),
                    JAVA_HOME: '/decoy/jdk',
                    PATH: "${javaBin}:/usr/bin:/bin",
            ])

            assertTrue(output, output.contains('Starting Odisee Service'))
            for (int i = 0; i < 100 && !Files.exists(envFile); i++) {
                Thread.sleep(50)
            }
            assertTrue(output, Files.isRegularFile(envFile))
            List<String> env = Files.readAllLines(envFile)
            assertFalse(env.any { it.startsWith('JAVA_HOME=') })
            assertFalse(env.any { it.startsWith('ODISEE_ETC=') })
            assertTrue(env.any { it == "ODISEE_HOME=${install}" })
            String path = env.find { it.startsWith('PATH=') }
            assertNotNull(path)
            assertFalse(path.contains("${install}/bin"))
            String args = Files.readString(argsFile)
            assertTrue(args, args.contains('-jar'))
            assertTrue(args, args.contains(install.resolve('application.jar').toString()))
        } finally {
            Path pidfile = install.resolve('var/odisee.pid')
            if (Files.exists(pidfile)) {
                try {
                    long pid = Long.parseLong(Files.readString(pidfile).trim())
                    ProcessHandle.of(pid).ifPresent { handle ->
                        String command = handle.info().command().orElse('')
                        String args = handle.info().arguments().map { it.join(' ') }.orElse('')
                        if ((command + ' ' + args).contains(install.toString())) {
                            handle.destroyForcibly()
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            PathTree.delete(tree)
        }
    }

    void testLoginProfileIsGone() {
        assertFalse(new File('src/main/container/shell_profile').exists())
        assertFalse(new File('src/main/container/etc/odienv').exists())
        String odictl = new File('src/main/container/bin/odictl').getText('UTF-8')
        String odilib = new File('src/main/container/bin/odilib').getText('UTF-8')
        String entry = new File('src/main/container/entrypoint.sh').getText('UTF-8')
        String containerfile = new File('src/main/container/Containerfile').getText('UTF-8')
        String gradle = new File('../build.gradle.kts').getText('UTF-8')
        assertFalse(odictl.contains('odienv'))
        assertFalse(odictl.contains('export PATH'))
        assertFalse(odictl.contains('.fonts'))
        assertFalse(odilib.contains('.fonts'))
        assertFalse(odilib.contains('export PATH'))
        assertTrue(odilib.contains('/.local/share/odisee'))
        assertFalse(entry.contains('odienv'))
        assertFalse(entry.contains('ls -lR'))
        assertTrue(entry.contains('odictl -q start-inst'))
        assertTrue(entry.contains('-jar ${ODISEE_HOME}/application.jar'))
        assertFalse(containerfile.contains('shell_profile'))
        assertFalse(containerfile.contains('.bash_profile'))
        assertTrue(containerfile.contains('/home/odisee/.local/bin'))
        assertTrue(containerfile.contains('COPY --chown=odisee:odisee --chmod=440 webservice*.jar application.jar'))
        assertFalse(containerfile.contains('xargs'))
        assertTrue(containerfile.lastIndexOf('COPY ') == containerfile.lastIndexOf('COPY --chown=odisee:odisee --chmod=440 webservice*.jar application.jar'))
        assertTrue(containerfile.indexOf('libreoffice-writer') < containerfile.indexOf('COPY '))
        assertFalse(gradle.contains('shell_profile'))
        assertFalse(gradle.contains('.bash_profile'))
    }

    void testBash4RunsOdictl() {
        String odilib = new File('src/main/container/bin/odilib').getText('UTF-8')
        int guard = odilib.indexOf('BASH_VERSINFO[0]')
        int arrays = odilib.indexOf('\ndeclare -A ')
        assertTrue(odilib, guard >= 0)
        assertTrue("guard=${guard} arrays=${arrays}", arrays > guard)
        assertTrue(odilib.contains('/opt/homebrew/bin/bash'))
        assertTrue(odilib.contains('/usr/local/bin/bash'))
        assertTrue(odilib.indexOf('/opt/homebrew/bin/bash') < odilib.indexOf('/usr/local/bin/bash'))
        assertTrue(odilib.contains('exec "${_odisee_bash}" "$0" "$@"'))
        assertTrue(odilib.contains('Odisee needs bash 4 or newer. On macOS: brew install bash.'))
        assertFalse(odilib.contains('BASH_SOURCE'))
        ['odictl', 'odipost', 'odiwatchdog'].each { String name ->
            String entry = new File("src/main/container/bin/${name}").getText('UTF-8')
            assertTrue(entry, entry.contains('. ${SCRIPT_DIR}/odilib'))
            assertTrue(entry, entry.indexOf('. ${SCRIPT_DIR}/odilib') < entry.indexOf('declare -A') || !entry.contains('declare -A'))
        }

        Path tree = Files.createTempDirectory('odictl-bash4')
        try {
            Path home = tree.resolve('home')
            Files.createDirectories(home)
            String output = launch(true, [bash4(), scriptPath(), 'callback-host', 'ctlbash', 'hooks.example'], [
                    HOME: home.toString(),
                    ODISEE_HOME: '',
                    JAVA_HOME: '',
                    PATH: '/usr/bin:/bin',
            ])
            assertFalse(output.contains('Odisee needs bash 4 or newer.'))
            assertTrue(output.contains("Callback host 'hooks.example' listed for 'ctlbash'."))
            assertTrue(Files.readString(home.resolve('.local/share/odisee/etc/callback-hosts')).contains('ctlbash hooks.example'))
        } finally {
            PathTree.delete(tree)
        }
    }

    void testDirectoryLockWhenFlockIsAbsent() {
        Path tree = Files.createTempDirectory('odictl-lock')
        try {
            Path home = tree.resolve('home')
            Path bindir = tree.resolve('bin')
            Files.createDirectories(home)
            Files.createDirectories(bindir)
            String bash = bash4()
            Path bashPath = bash == 'bash'
                    ? Path.of(['bash', '-c', 'command -v bash'].execute().text.trim())
                    : Path.of(bash)
            Files.createSymbolicLink(bindir.resolve('bash'), bashPath)
            ['mkdir', 'cat', 'rm', 'sleep', 'uname', 'tr', 'dirname', 'readlink', 'mktemp', 'chmod', 'mv'].each { String tool ->
                String found = ['bash', '-c', "command -v ${tool}"].execute().text.trim()
                if (!found.isEmpty()) {
                    Files.createSymbolicLink(bindir.resolve(tool), Path.of(found))
                }
            }
            String output = launch(true, [scriptPath(), 'callback-host', 'ctllock', 'hooks.example'], [
                    HOME: home.toString(),
                    ODISEE_HOME: '',
                    JAVA_HOME: '',
                    PATH: bindir.toString(),
            ])
            assertTrue(output, output.contains("Callback host 'hooks.example' listed for 'ctllock'."))
            assertTrue(Files.readString(home.resolve('.local/share/odisee/etc/callback-hosts')).contains('ctllock hooks.example'))
            assertFalse(Files.exists(home.resolve('.local/share/odisee/etc/.odictl.lock.d')))
        } finally {
            PathTree.delete(tree)
        }
    }

    void testLinuxOfficePortCheckReadsProcNet() {
        Path tree = Files.createTempDirectory('odisee-proc')
        InetAddress loopback = InetAddress.getByName('127.0.0.1')
        ServerSocket server = new ServerSocket(0, 50, loopback)
        Socket client = null
        Socket accepted = null
        try {
            int port = server.localPort
            client = new Socket(loopback, port)
            accepted = server.accept()
            Path home = tree.resolve('odisee')
            Files.createDirectories(home.resolve('etc'))
            Files.writeString(home.resolve('etc/odiinst'), "odi1|127.0.0.1|${port}|/usr/lib/libreoffice||nologo|true\n")
            Path tcp = tree.resolve('tcp')
            Path tcp6 = tree.resolve('tcp6')
            Path missing = tree.resolve('missing-tcp')
            Path err = tree.resolve('err.txt')
            // 2001 is 07D1. One LISTEN and one ESTABLISHED on that local port, plus decoys.
            Files.writeString(tcp, [
                    '  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode',
                    '   0: 0100007F:07D1 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 1 1 0 100 0 0 10 0',
                    '   1: 0100007F:07d1 0100007F:1234 01 00000000:00000000 00:00000000 00000000     0        0 2 1 0 100 0 0 10 0',
                    '   2: 0100007F:07D2 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 3 1 0 100 0 0 10 0',
                    '   3: 0100007F:C001 0100007F:07D1 01 00000000:00000000 00:00000000 00000000     0        0 4 1 0 100 0 0 10 0',
                    '   4: 0100007F:07D1 00000000:0000 06 00000000:00000000 00:00000000 00000000     0        0 5 1 0 100 0 0 10 0',
                    '',
            ].join('\n'))
            Files.writeString(tcp6, [
                    '  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode',
                    '   0: 00000000000000000000000000000000:07D1 00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 00000000 0 0 1 1 0 100 0 0 10 0',
                    '   1: 00000000000000000000000000000000:07D1 0000000000000000FFFF00000100007F:1234 01 00000000:00000000 00:00000000 00000000 0 0 2 1 0 100 0 0 10 0',
                    '',
            ].join('\n'))
            // The fixture files are the parser. A live listener is the Linux
            // production path (/proc/net/tcp). macOS netstat is a different tool.
            List<String> lines = [
                    '. "$ODILIB"',
                    'listen=$(proc_tcp_count 2001 0A "$TCP" "$TCP6")',
                    'echo "listen=$listen"',
                    'estab=$(proc_tcp_count 2001 01 "$TCP" "$TCP6")',
                    'echo "estab=$estab"',
                    'missing=$(proc_tcp_count 2001 0A "$MISSING" "$MISSING" 2>"$ERR")',
                    'echo "missing=$missing"',
                    'errbytes=$(wc -c < "$ERR" | tr -d " ")',
                    'echo "errbytes=$errbytes"',
            ]
            if (linuxHost()) {
                lines.add('get_inst_status odi1')
                lines.add('echo "numlist=${INSTSTAT[numlist]}"')
                lines.add('echo "numconn=${INSTSTAT[numconn]}"')
            }
            String output = launch(true, [bash4(), '-c', lines.join('\n')], [
                    ODISEE_HOME: home.toString(),
                    HOME: home.toString(),
                    PATH: '/usr/bin:/bin',
                    ODILIB: new File('src/main/container/bin/odilib').absolutePath,
                    TCP: tcp.toString(),
                    TCP6: tcp6.toString(),
                    MISSING: missing.toString(),
                    ERR: err.toString(),
            ])

            assertEquals(output, '2', value(output, 'listen'))
            assertEquals(output, '2', value(output, 'estab'))
            assertEquals(output, '0', value(output, 'missing'))
            assertEquals(output, '0', value(output, 'errbytes'))
            if (linuxHost()) {
                assertTrue(output, Integer.parseInt(value(output, 'numlist')) >= 1)
                assertTrue(output, Integer.parseInt(value(output, 'numconn')) >= 1)
            }
            String library = new File('src/main/container/bin/odilib').getText('UTF-8')
            String watchdog = new File('src/main/container/bin/odiwatchdog').getText('UTF-8')
            String containerfile = new File('src/main/container/Containerfile').getText('UTF-8')
            assertTrue(library.contains('-an -p tcp'))
            assertTrue(library.contains('/usr/sbin/netstat'))
            assertFalse(library.contains('ss -lnt'))
            assertTrue(watchdog.contains('proc_tcp_count'))
            assertFalse(watchdog.contains('ss -nlt'))
            assertTrue(watchdog.contains('sockstat -46l'))
            assertFalse(containerfile.contains('iproute2'))
            assertFalse(containerfile.contains('net-tools'))
            assertTrue(containerfile.contains('FROM ubuntu:26.04'))
            assertTrue(containerfile.contains('CMD ["/home/odisee/entrypoint.sh"]'))
        } finally {
            if (accepted != null) {
                accepted.close()
            }
            if (client != null) {
                client.close()
            }
            server.close()
            PathTree.delete(tree)
        }
    }

    void testEntrypointKeepsJavaInTheForeground() {
        Path tree = Files.createTempDirectory('odisee-foreground')
        EntrypointRun run = null
        try {
            run = entrypointRun(tree, [:])
            boolean started = waitForFile(run.javaArgs, run.process)
            assertTrue(started ? 'java started' : readUntil(run.process, 'NEVER', 200), started)
            String cmd = commandLine(run.process.pid())
            assertTrue(cmd, cmd.contains('entrypoint.sh'))
            assertFalse(cmd, cmd.contains('--rcfile'))
            assertFalse(cmd, cmd.contains('--login'))
            String args = Files.readString(run.javaArgs)
            assertTrue(args, args.contains('-jar'))
            assertTrue(args, args.contains(run.home.resolve('application.jar').toString()))
            assertEquals('-q start-inst', Files.readString(run.odictlArgs).trim())
            String entry = new File('src/main/container/entrypoint.sh').getText('UTF-8')
            assertFalse(entry.contains('tail -f'))
            assertFalse(entry.contains('--login'))
        } finally {
            stopRun(run)
            PathTree.delete(tree)
        }
    }

    void testDebugShellStopsTheJvmAndLibreOfficeOnExit() {
        debugShell(false)
    }

    void testDebugShellStopsTheJvmAndLibreOfficeOnSigterm() {
        debugShell(true)
    }

    void testWatchdogRestartsClosedPortWithStartInstUnderNounset() {
        Path tree = Files.createTempDirectory('odisee-watchdog-restart')
        Process watchdog = null
        try {
            int closedPort
            new ServerSocket(0, 50, InetAddress.getByName('127.0.0.1')).withCloseable { ServerSocket s ->
                closedPort = s.localPort
            }
            Path home = tree.resolve('odisee')
            Path shim = tree.resolve('shim')
            Path calls = tree.resolve('odictl.calls')
            Files.createDirectories(home.resolve('etc'))
            Files.createDirectories(shim)
            Files.writeString(home.resolve('etc/odiinst'),
                    "odi1|127.0.0.1|${closedPort}|/usr/lib/libreoffice||nologo|true|group0\n")
            writeExe(shim.resolve('odictl'), [
                    '#!/usr/bin/env bash',
                    "printf '%s\\n' \"\$*\" >> '${calls}'",
                    'exit 0',
                    '',
            ].join('\n'))

            ProcessBuilder builder = new ProcessBuilder([bash4(), watchdogScriptPath(), '-i', 'odi1'])
            builder.environment().put('ODISEE_HOME', home.toString())
            builder.environment().put('HOME', home.toString())
            builder.environment().put('ODISEE_WD_TIMEOUT', '0.1')
            builder.environment().put('PATH', (shim.toString() + ':/usr/bin:/bin:/usr/sbin:/sbin'))
            builder.redirectErrorStream(true)
            watchdog = builder.start()

            String out = readUntil(watchdog, "restarting instance 'odi1'", 10000)
            for (int i = 0; i < 100 && !Files.exists(calls); i++) {
                Thread.sleep(50)
            }
            assertTrue(out, out.contains("Start watching instance 'odi1'."))
            assertTrue(out, out.contains("Port ${closedPort} closed, restarting instance 'odi1'."))
            assertFalse(out, out.contains('unbound variable'))
            assertTrue(out, Files.isRegularFile(calls))
            List<String> lines = Files.readAllLines(calls)
            assertFalse(lines.isEmpty())
            assertEquals('-q start-inst -i odi1', lines[0].trim())
        } finally {
            terminateProcessTree(watchdog)
            PathTree.delete(tree)
        }
    }

    void testWatchdogDetectsAlreadyRunningInstanceAndSupportsHelp() {
        Path tree = Files.createTempDirectory('odisee-watchdog-dup')
        ServerSocket listener = new ServerSocket(0, 50, InetAddress.getByName('127.0.0.1'))
        Process first = null
        try {
            Path home = tree.resolve('odisee')
            Files.createDirectories(home.resolve('etc'))
            String inst = 'odidupcheck'
            Files.writeString(home.resolve('etc/odiinst'),
                    "${inst}|127.0.0.1|${listener.localPort}|/usr/lib/libreoffice||nologo|true\n")

            String help = launch(true, [bash4(), watchdogScriptPath(), '-h'], [
                    ODISEE_HOME: home.toString(),
                    HOME: home.toString(),
                    PATH: '/usr/bin:/bin',
            ])
            assertTrue(help, help.contains('-i <instance>'))

            ProcessBuilder builder = new ProcessBuilder([bash4(), watchdogScriptPath(), '-i', inst])
            builder.environment().put('ODISEE_HOME', home.toString())
            builder.environment().put('HOME', home.toString())
            builder.environment().put('ODISEE_WD_TIMEOUT', '0.2')
            builder.environment().put('PATH', '/usr/bin:/bin:/usr/sbin:/sbin')
            builder.redirectErrorStream(true)
            first = builder.start()

            String started = readUntil(first, "Start watching instance '${inst}'.", 10000)
            assertTrue(started, started.contains("Start watching instance '${inst}'."))
            assertTrue(started, first.isAlive())

            String second = launch(true, [bash4(), watchdogScriptPath(), '-i', inst], [
                    ODISEE_HOME: home.toString(),
                    HOME: home.toString(),
                    ODISEE_WD_TIMEOUT: '0.2',
                    PATH: '/usr/bin:/bin:/usr/sbin:/sbin',
            ])
            assertTrue(second, second.contains("There is a watchdog for instance '${inst}' already."))
        } finally {
            terminateProcessTree(first)
            listener.close()
            PathTree.delete(tree)
        }
    }

    void testOdictlWatchFlagLaunchesWatchdogAndRequiresInstance() {
        Path tree = Files.createTempDirectory('odisee-ctl-watch')
        ServerSocket listener = new ServerSocket(0, 50, InetAddress.getByName('127.0.0.1'))
        String inst = 'odiwatchflag'
        try {
            Path home = tree.resolve('odisee')
            Files.createDirectories(home.resolve('etc'))
            Files.writeString(home.resolve('etc/odiinst'),
                    "${inst}|127.0.0.1|${listener.localPort}|/usr/lib/libreoffice||nologo|true\n")

            String missingInst = launch(true, [bash4(), scriptPath(), '-w'], [
                    ODISEE_HOME: home.toString(),
                    HOME: home.toString(),
                    PATH: '/usr/bin:/bin',
            ])
            assertTrue(missingInst, missingInst.contains('Option -w needs -i.'))
            assertFalse(missingInst, missingInst.contains('Unknown option -w'))

            String launched = launch(true, [bash4(), scriptPath(), '-w', '-i', inst], [
                    ODISEE_HOME: home.toString(),
                    HOME: home.toString(),
                    ODISEE_WD_TIMEOUT: '0.2',
                    PATH: '/usr/bin:/bin:/usr/sbin:/sbin',
            ])
            assertFalse(launched, launched.contains('Unknown option'))
            assertFalse(launched, launched.contains('needs one of the following options'))

            Path logFile = home.resolve("var/log/watchdog_${inst}.log")
            for (int i = 0; i < 100 && (!Files.isRegularFile(logFile) || !Files.readString(logFile).contains("Start watching instance '${inst}'.")); i++) {
                Thread.sleep(50)
            }
            assertTrue(Files.isRegularFile(logFile))
            assertTrue(Files.readString(logFile), Files.readString(logFile).contains("Start watching instance '${inst}'."))
        } finally {
            killMatchingCommandLine(inst)
            listener.close()
            PathTree.delete(tree)
        }
    }

    void testAutostartFindsInstancesWithEighthGroupColumn() {
        Path tree = Files.createTempDirectory('odisee-autostart-group')
        try {
            Path home = tree.resolve('odisee')
            Path loHome = tree.resolve('lo')
            Path started = tree.resolve('started.txt')
            Files.createDirectories(home.resolve('etc'))
            Files.createDirectories(loHome.resolve('program'))
            Files.createDirectories(loHome.resolve('Contents/MacOS'))
            String sofficeStub = [
                    '#!/usr/bin/env bash',
                    "printf '%s\\n' \"\$*\" >> '${started}'",
                    'exit 0',
                    '',
            ].join('\n')
            writeExe(loHome.resolve('program/soffice'), sofficeStub)
            writeExe(loHome.resolve('Contents/MacOS/soffice'), sofficeStub)

            Files.writeString(home.resolve('etc/odiinst'), [
                    '# instances with and without group column',
                    "odi1|127.0.0.1|29101|${loHome}||nologo|true|writers",
                    "odi2|127.0.0.1|29102|${loHome}||nologo|false|writers",
                    "odi3|127.0.0.1|29103|${loHome}||nologo|true",
                    '',
            ].join('\n'))

            String out = launch(true, [bash4(), scriptPath(), '-q', 'start-inst'], [
                    ODISEE_HOME: home.toString(),
                    HOME: home.toString(),
                    PATH: '/usr/bin:/bin:/usr/sbin:/sbin',
            ])
            for (int i = 0; i < 60 && (!Files.isRegularFile(started) || Files.readAllLines(started).size() < 2); i++) {
                Thread.sleep(50)
            }
            assertFalse(out, out.contains('No instances configured for autostart.'))
            assertTrue(out, Files.isRegularFile(started))
            String log = Files.readString(started)
            assertTrue(log, log.contains('port=29101'))
            assertTrue(log, log.contains('port=29103'))
            assertFalse(log, log.contains('port=29102'))
        } finally {
            PathTree.delete(tree)
        }
    }

    void testUserPreservesExistingAudienceAndUiClientInOidcFile() {
        Files.createDirectories(OidcFile.location().parent)
        Files.writeString(OidcFile.location(), [
                '# Custom OIDC settings',
                'issuer http://previous.example/realms/odisee',
                'audience custom-aud',
                'ui-client custom-portal',
                '',
        ].join('\n'))

        String reserved = denied('user', 'custom-portal')
        assertTrue(reserved, reserved.contains('Invalid user'))

        succeed('user', 'ctlada')

        OidcFile.Record record = OidcFile.read(OidcFile.location())
        assertEquals(issuer, record.issuer)
        assertEquals('custom-aud', record.audience)
        assertEquals('custom-portal', record.uiClient)
        assertFalse(createdClients.isEmpty())
        assertTrue(createdClients.last(), createdClients.last().contains('"included.custom.audience":"custom-aud"'))
    }

    void testManageAndRemoveInstanceKeepDataPathGroupAndAnchoredMatch() {
        Path tree = Files.createTempDirectory('odisee-manage-inst')
        try {
            Path home = tree.resolve('odisee')
            Path instFile = home.resolve('etc/odiinst')
            Files.createDirectories(home.resolve('etc'))
            Files.writeString(instFile, [
                    'odi10|127.0.0.1|29210|/opt/lo|/custom/odi10|nologo|true|writers',
                    'odi1|127.0.0.1|29201|/opt/lo|/custom/odi1|nologo nodefault|true|group0',
                    '',
            ].join('\n'))

            String modOut = launch(true, [bash4(), scriptPath(), '-m', '-i', 'odi1', '-p', '127.0.0.1:29215', '-o', 'nodefault=false'], [
                    ODISEE_HOME: home.toString(),
                    HOME: home.toString(),
                    PATH: '/usr/bin:/bin:/usr/sbin:/sbin',
            ])
            assertTrue(modOut, modOut.contains("Instance 'odi1' modified."))
            List<String> rows = Files.readAllLines(instFile).findAll { it.trim() }
            assertEquals('odi10|127.0.0.1|29210|/opt/lo|/custom/odi10|nologo|true|writers', rows[0])
            assertEquals('odi1|127.0.0.1|29215|/opt/lo|/custom/odi1|nologo|true|group0', rows[1])
            assertFalse(Files.readString(instFile).contains('yalt'))
            Files.list(home.resolve('etc')).withCloseable { stream ->
                assertEquals(['odiinst'], stream.collect { it.fileName.toString() })
            }

            String rmOut = launch(true, [bash4(), scriptPath(), '-r', '-i', 'odi1'], [
                    ODISEE_HOME: home.toString(),
                    HOME: home.toString(),
                    PATH: '/usr/bin:/bin:/usr/sbin:/sbin',
            ])
            assertTrue(rmOut, rmOut.contains("Instance 'odi1' removed."))
            List<String> remaining = Files.readAllLines(instFile).findAll { it.trim() }
            assertEquals(['odi10|127.0.0.1|29210|/opt/lo|/custom/odi10|nologo|true|writers'], remaining)
        } finally {
            PathTree.delete(tree)
        }
    }

    void testGetFreePortSortsNumericallyAndFindInstallationChecksUsrLibLibreOffice() {
        Path tree = Files.createTempDirectory('odisee-odilib-helpers')
        try {
            Path home = tree.resolve('odisee')
            Path fakeRoot = tree.resolve('usr/lib/libreoffice')
            Files.createDirectories(home.resolve('etc'))
            Files.createDirectories(fakeRoot.resolve('program'))
            Files.writeString(home.resolve('etc/odiinst'), [
                    '# comment',
                    'odi1|127.0.0.1|2009|/usr/lib/libreoffice||nologo|true',
                    'odi2|127.0.0.1|10000|/usr/lib/libreoffice||nologo|true',
                    '',
            ].join('\n'))

            String script = [
                    '. "$ODILIB"',
                    'set -o nounset',
                    'echo "next_port=$(get_free_port)"',
                    'ODISEE_OS=Linux',
                    'find_installation',
                    'echo "found=${FOUND_HOME}"',
                    'OOO_UNOPKG=/bin/echo',
                    'echo "rm_ext=$(remove_extension vnd.artofcoding.Ext shared | head -n 1)"',
            ].join('\n')

            String out = launch(true, [bash4(), '-c', script], [
                    ODISEE_HOME: home.toString(),
                    HOME: home.toString(),
                    PATH: '/usr/bin:/bin',
                    ODILIB: new File('src/main/container/bin/odilib').absolutePath,
                    ODISEE_LO_SEARCH_PATHS: "${tree.resolve('missing')} ${fakeRoot}",
            ])
            assertEquals(out, '10001', value(out, 'next_port'))
            assertEquals(out, fakeRoot.toString(), value(out, 'found'))
            assertEquals(out, 'Removing extension vnd.artofcoding.Ext (options: --shared)', value(out, 'rm_ext'))

            byte[] odilibBytes = Files.readAllBytes(Path.of('src/main/container/bin/odilib'))
            for (int i = 0; i < odilibBytes.length - 1; i++) {
                assertFalse("NBSP at byte ${i}", (odilibBytes[i] & 0xFF) == 0xC2 && (odilibBytes[i + 1] & 0xFF) == 0xA0)
            }
            String odilibText = new String(odilibBytes, StandardCharsets.UTF_8)
            assertTrue(odilibText.contains('/usr/lib/libreoffice'))
            assertFalse(odilibText.contains('$SED -i'))
        } finally {
            PathTree.delete(tree)
        }
    }

    void testOdipostBearerAndVarRequestFallbackWithoutEval() {
        Path tree = Files.createTempDirectory('odisee-odipost-bearer')
        Map<String, Object> received = Collections.synchronizedMap([:])
        HttpServer server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        server.createContext('/odisee/document/generate', { HttpExchange exchange ->
            received.method = exchange.requestMethod
            received.auth = exchange.requestHeaders.getFirst('Authorization')
            received.contentType = exchange.requestHeaders.getFirst('Content-Type')
            received.body = new String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
            byte[] pdf = '%PDF-1.4 bearer'.getBytes(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, pdf.length)
            exchange.responseBody.withCloseable { it.write(pdf) }
        } as HttpHandler)
        server.start()
        try {
            Path home = tree.resolve('odisee')
            Files.createDirectories(home.resolve('var/request'))
            Files.writeString(home.resolve('var/request/sample.xml'), '<odisee><request/></odisee>')
            Path sentinel = tree.resolve('pwned')
            Path outFile = tree.resolve('out $(touch pwned).pdf')

            ProcessBuilder pb = new ProcessBuilder([
                    bash4(), odipostScriptPath(),
                    '-h', '127.0.0.1',
                    '-p', Integer.toString(server.address.port),
                    '--bearer', 'jwt-token-123',
                    '-f', 'sample.xml',
                    '-o', outFile.toString(),
            ])
            pb.directory(tree.toFile())
            pb.environment().put('ODISEE_HOME', home.toString())
            pb.environment().put('HOME', home.toString())
            pb.environment().put('PATH', '/usr/bin:/bin')
            pb.redirectErrorStream(true)
            Process proc = pb.start()
            String postOut = proc.inputStream.getText('UTF-8')
            assertTrue(postOut, proc.waitFor(30, TimeUnit.SECONDS))
            assertEquals(postOut, 0, proc.exitValue())

            assertFalse('eval executed shell metacharacters', Files.exists(sentinel))
            assertTrue(Files.isRegularFile(outFile))
            assertEquals('%PDF-1.4 bearer', Files.readString(outFile))
            assertEquals('POST', received.method)
            assertEquals('Bearer jwt-token-123', received.auth)
            assertEquals('text/xml', received.contentType)
            assertEquals('<odisee><request/></odisee>', received.body)

            String missing = launch(false, [
                    bash4(), odipostScriptPath(),
                    '-h', '127.0.0.1',
                    '-p', Integer.toString(server.address.port),
                    '-f', 'does-not-exist.xml',
            ], [
                    ODISEE_HOME: home.toString(),
                    HOME: home.toString(),
                    PATH: '/usr/bin:/bin',
            ])
            assertTrue(missing, missing.contains(home.resolve('var/request/does-not-exist.xml').toString()))
            assertFalse(missing, missing.contains('var/test'))
        } finally {
            server.stop(0)
            PathTree.delete(tree)
        }
    }

    void testOdipostEnvTokenGzipAndAbsoluteJsonRequest() {
        Path tree = Files.createTempDirectory('odisee-odipost-gzip')
        Map<String, Object> received = Collections.synchronizedMap([:])
        HttpServer server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        server.createContext('/odisee/document/generate', { HttpExchange exchange ->
            received.auth = exchange.requestHeaders.getFirst('Authorization')
            received.contentType = exchange.requestHeaders.getFirst('Content-Type')
            received.encoding = exchange.requestHeaders.getFirst('Content-Encoding')
            received.raw = exchange.requestBody.readAllBytes()
            byte[] pdf = '%PDF-1.4 gzip'.getBytes(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, pdf.length)
            exchange.responseBody.withCloseable { it.write(pdf) }
        } as HttpHandler)
        server.start()
        try {
            Path home = tree.resolve('odisee')
            Files.createDirectories(home)
            Path reqJson = tree.resolve('abs-request.json')
            String payload = '{"request":{"template":{"name":"Letter"}}}'
            Files.writeString(reqJson, payload)
            Path outFile = tree.resolve('result.pdf')

            launch(true, [
                    bash4(), odipostScriptPath(),
                    '-h', '127.0.0.1',
                    '-p', Integer.toString(server.address.port),
                    '-z',
                    '-f', reqJson.toString(),
                    '-o', outFile.toString(),
            ], [
                    ODISEE_HOME: home.toString(),
                    HOME: home.toString(),
                    ODISEE_TOKEN: 'env-token-456',
                    PATH: '/usr/bin:/bin',
            ])

            assertEquals('Bearer env-token-456', received.auth)
            assertEquals('application/json', received.contentType)
            assertEquals('gzip', received.encoding)
            String unpacked = new java.util.zip.GZIPInputStream(new ByteArrayInputStream((byte[]) received.raw)).getText('UTF-8')
            assertEquals(payload, unpacked)
            assertFalse(Files.exists(tree.resolve('abs-request.json.gz')))
            assertEquals('%PDF-1.4 gzip', Files.readString(outFile))
        } finally {
            server.stop(0)
            PathTree.delete(tree)
        }
    }

    void testOdidevRecognizesLinuxAndStartsSoffice() {
        Path tree = Files.createTempDirectory('odisee-odidev')
        try {
            Path home = tree.resolve('home')
            Path bin = tree.resolve('bin')
            Path loDir = tree.resolve('libreoffice/program')
            Path calls = tree.resolve('soffice.calls')
            Files.createDirectories(home)
            Files.createDirectories(bin)
            Files.createDirectories(loDir)
            writeExe(loDir.resolve('soffice'), [
                    '#!/usr/bin/env bash',
                    "printf '%s|%s\\n' \"\$PWD\" \"\$*\" >> '${calls}'",
                    'exit 0',
                    '',
            ].join('\n'))
            Files.createSymbolicLink(bin.resolve('soffice'), Path.of('../libreoffice/program/soffice'))
            writeExe(bin.resolve('uname'), "#!/bin/sh\nprintf 'Linux\\n'\n")

            String out = launch(true, [bash4(), odidevScriptPath(), 'start'], [
                    HOME: home.toString(),
                    PATH: "${bin}:/usr/bin:/bin",
            ])
            for (int i = 0; i < 60 && (!Files.isRegularFile(calls) || Files.readAllLines(calls).size() < 2); i++) {
                Thread.sleep(50)
            }
            assertFalse(out, out.contains('unsupported operating system'))
            assertTrue(out, Files.isRegularFile(calls))
            String log = Files.readString(calls)
            assertTrue(log, log.contains("${loDir.toRealPath()}|"))
            assertTrue(log, log.contains('port=2001'))
            assertTrue(log, log.contains('port=2002'))

            String badMode = launch(false, [bash4(), odidevScriptPath(), 'unknown'], [
                    HOME: home.toString(),
                    PATH: "${bin}:/usr/bin:/bin",
            ])
            assertTrue(badMode, badMode.contains('Unknown mode'))
        } finally {
            PathTree.delete(tree)
        }
    }

    private void debugShell(boolean sigterm) {
        Path tree = Files.createTempDirectory(sigterm ? 'odisee-term' : 'odisee-exit')
        EntrypointRun run = null
        try {
            run = entrypointRun(tree, [ODISEE_SHELL: '1'])
            run.process.getOutputStream().write('echo SHELL_READY\n'.getBytes(StandardCharsets.UTF_8))
            run.process.getOutputStream().flush()
            String output = readUntil(run.process, 'SHELL_READY', 20000)
            assertTrue(output, output.contains('SHELL_READY'))
            String cmd = commandLine(run.process.pid())
            assertTrue(cmd, cmd.contains('--rcfile'))
            assertFalse(cmd, cmd.contains('--login'))
            if (sigterm) {
                run.process.destroy()
            } else {
                run.process.getOutputStream().write('exit\n'.getBytes(StandardCharsets.UTF_8))
                run.process.getOutputStream().flush()
            }
            assertTrue(output, run.process.waitFor(15, TimeUnit.SECONDS))
            assertTrue(output, died(run.javaPid))
            assertTrue(output, died(run.sofficePid))
        } finally {
            stopRun(run)
            PathTree.delete(tree)
        }
    }

    private EntrypointRun entrypointRun(Path tree, Map<String, String> extra) {
        EntrypointRun run = new EntrypointRun()
        run.home = tree.resolve('odisee')
        run.bin = tree.resolve('bin')
        run.javaPid = tree.resolve('java.pid')
        run.javaArgs = tree.resolve('java.args')
        run.sofficePid = tree.resolve('soffice.pid')
        run.odictlArgs = tree.resolve('odictl.args')
        Files.createDirectories(run.home)
        Files.createDirectories(run.bin)
        writeExe(run.bin.resolve('java'), [
                '#!/bin/bash',
                "trap '' HUP",
                'echo $$ > "$JAVA_PIDFILE"',
                'printf %s\\\\n "$*" > "$JAVA_ARGS"',
                'trap \'exit 0\' TERM',
                'while true; do sleep 0.2; done',
                '',
        ].join('\n'))
        writeExe(run.bin.resolve('soffice'), [
                '#!/bin/bash',
                "trap '' HUP",
                'echo $$ > "$SOFFICE_PIDFILE"',
                'trap \'exit 0\' TERM',
                'while true; do sleep 0.2; done',
                '',
        ].join('\n'))
        writeExe(run.bin.resolve('odictl'), [
                '#!/bin/bash',
                'printf %s\\\\n "$*" > "$ODICTL_ARGS"',
                'if [ "$1" = "-q" ] && [ "$2" = "start-inst" ]; then',
                "    trap '' HUP",
                '    "$SOFFICE_BIN" "$ODISEE_HOME" &',
                '    trap - HUP',
                'fi',
                'exit 0',
                '',
        ].join('\n'))
        ProcessBuilder builder = new ProcessBuilder(['/bin/bash', new File('src/main/container/entrypoint.sh').absolutePath])
        builder.environment().put('ODISEE_HOME', run.home.toString())
        builder.environment().put('HOME', run.home.toString())
        builder.environment().put('PATH', run.bin.toString() + ':/usr/bin:/bin')
        builder.environment().put('JAVA_PIDFILE', run.javaPid.toString())
        builder.environment().put('JAVA_ARGS', run.javaArgs.toString())
        builder.environment().put('SOFFICE_PIDFILE', run.sofficePid.toString())
        builder.environment().put('SOFFICE_BIN', run.bin.resolve('soffice').toString())
        builder.environment().put('ODICTL_ARGS', run.odictlArgs.toString())
        extra.each { String key, String value ->
            builder.environment().put(key, value)
        }
        builder.redirectErrorStream(true)
        run.process = builder.start()
        run
    }

    private static String value(String output, String key) {
        output.readLines().find { it.startsWith(key + '=') }?.substring(key.length() + 1)?.trim()
    }

    private static void writeExe(Path path, String text) {
        Files.writeString(path, text)
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString('rwxr-xr-x'))
    }

    private static boolean waitForFile(Path file, Process process) {
        for (int i = 0; i < 300 && !Files.exists(file); i++) {
            if (!process.isAlive()) {
                return false
            }
            Thread.sleep(50)
        }
        Files.isRegularFile(file)
    }

    private static String readUntil(Process process, String token, long timeoutMs) {
        StringBuilder out = new StringBuilder()
        long deadline = System.currentTimeMillis() + timeoutMs
        byte[] buf = new byte[512]
        while (System.currentTimeMillis() < deadline && !out.toString().contains(token)) {
            if (process.getInputStream().available() > 0) {
                int n = process.getInputStream().read(buf)
                if (n < 0) {
                    break
                }
                out.append(new String(buf, 0, n, StandardCharsets.UTF_8))
            } else if (!process.isAlive()) {
                break
            } else {
                Thread.sleep(50)
            }
        }
        out.toString()
    }

    private static String commandLine(long pid) {
        Path proc = Path.of('/proc', Long.toString(pid), 'cmdline')
        if (Files.isRegularFile(proc)) {
            return Files.readString(proc, StandardCharsets.ISO_8859_1).replace('\u0000', ' ')
        }
        Process ps = new ProcessBuilder('ps', '-p', Long.toString(pid), '-ww', '-o', 'args=').start()
        String text = ps.inputStream.getText('UTF-8')
        ps.waitFor(5, TimeUnit.SECONDS)
        text.trim()
    }

    /**
     * IntelliJ on macOS puts {@code /bin/bash} (3.2) on {@code PATH}.
     * The same candidates {@code odilib} re-execs are used here.
     */
    private static boolean linuxHost() {
        System.getProperty('os.name', '').contains('Linux')
    }

    private static String bash4() {
        Integer onPath = bashMajor('bash')
        if (onPath != null && onPath >= 4) {
            return 'bash'
        }
        for (String candidate : ['/opt/homebrew/bin/bash', '/usr/local/bin/bash']) {
            if (new File(candidate).canExecute()) {
                Integer major = bashMajor(candidate)
                if (major != null && major >= 4) {
                    return candidate
                }
            }
        }
        fail('Odisee needs bash 4 or newer. On macOS: brew install bash.')
        return 'bash'
    }

    private static Integer bashMajor(String bash) {
        String text = [bash, '-c', 'printf %s "${BASH_VERSINFO[0]}"'].execute().text.trim()
        if (!text.isInteger()) {
            return null
        }
        Integer.parseInt(text)
    }

    private static boolean died(Path pidFile) {
        long pid = Long.parseLong(Files.readString(pidFile).trim())
        for (int i = 0; i < 50; i++) {
            boolean alive = ProcessHandle.of(pid).map { ProcessHandle handle -> handle.isAlive() }.orElse(false)
            if (!alive) {
                return true
            }
            Thread.sleep(50)
        }
        false
    }

    private static void stopRun(EntrypointRun run) {
        if (run == null) {
            return
        }
        if (run.process != null && run.process.isAlive()) {
            run.process.destroyForcibly()
            run.process.waitFor(5, TimeUnit.SECONDS)
        }
        stopPid(run.javaPid)
        stopPid(run.sofficePid)
    }

    private static void stopPid(Path file) {
        if (file == null || !Files.exists(file)) {
            return
        }
        try {
            long pid = Long.parseLong(Files.readString(file).trim())
            ProcessHandle.of(pid).ifPresent { ProcessHandle handle -> handle.destroyForcibly() }
        } catch (NumberFormatException ignored) {
        }
    }

    private static final class EntrypointRun {
        Path home
        Path bin
        Path javaPid
        Path javaArgs
        Path sofficePid
        Path odictlArgs
        Process process
    }

    private String succeed(String... args) {
        run(true, [:], args)
    }

    private String denied(String... args) {
        run(false, [:], args)
    }

    private String run(boolean ok, Map<String, String> extra, String... args) {
        File script = new File('src/main/container/bin/odictl')
        assertTrue(script.file)
        String home = System.getProperty('ODISEE_HOME')
        assertNotNull(home)
        ProcessBuilder builder = new ProcessBuilder([script.absolutePath, *args])
        builder.environment().put('ODISEE_HOME', home)
        builder.environment().put('ODISEE_KEYCLOAK_ISSUER', issuer)
        builder.environment().put('ODISEE_KEYCLOAK_ADMIN', 'admin')
        builder.environment().put('ODISEE_KEYCLOAK_ADMIN_PASSWORD', 'admin-secret')
        extra.each { String key, String value ->
            if (value == null || value.isEmpty()) {
                builder.environment().remove(key)
            } else {
                builder.environment().put(key, value)
            }
        }
        builder.redirectErrorStream(true)
        Process process = builder.start()
        String output = process.inputStream.getText('UTF-8')
        boolean finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        assertTrue(output, finished)
        if (ok) {
            assertEquals(output, 0, process.exitValue())
        } else {
            assertTrue(output, process.exitValue() != 0)
        }
        output
    }

    private static String scriptPath() {
        File script = new File('src/main/container/bin/odictl')
        assertTrue(script.file)
        script.absolutePath
    }

    private static String watchdogScriptPath() {
        File script = new File('src/main/container/bin/odiwatchdog')
        assertTrue(script.file)
        script.absolutePath
    }

    private static String odipostScriptPath() {
        File script = new File('src/main/container/bin/odipost')
        assertTrue(script.file)
        script.absolutePath
    }

    private static String odidevScriptPath() {
        File script = new File('../bin/odidev')
        assertTrue(script.file)
        script.absolutePath
    }

    private static void terminateProcessTree(Process process) {
        if (process == null) {
            return
        }
        process.descendants().forEach { ProcessHandle child -> child.destroyForcibly() }
        process.destroyForcibly()
        process.waitFor(5, TimeUnit.SECONDS)
    }

    private static void killMatchingCommandLine(String token) {
        ProcessHandle.allProcesses().forEach { ProcessHandle handle ->
            String cmd = handle.info().commandLine().orElse('')
            if (cmd.contains(token) && handle.pid() != ProcessHandle.current().pid()) {
                handle.descendants().forEach { ProcessHandle child -> child.destroyForcibly() }
                handle.destroyForcibly()
            }
        }
    }

    private String launch(boolean ok, List<String> command, Map<String, String> env) {
        ProcessBuilder builder = new ProcessBuilder(command)
        env.each { String key, String value ->
            if (value == null || value.isEmpty()) {
                builder.environment().remove(key)
            } else {
                builder.environment().put(key, value)
            }
        }
        builder.redirectErrorStream(true)
        Process process = builder.start()
        String output = process.inputStream.getText('UTF-8')
        boolean finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        assertTrue(output, finished)
        if (ok) {
            assertEquals(output, 0, process.exitValue())
        } else {
            assertTrue(output, process.exitValue() != 0)
        }
        output
    }

    private static HttpServer fakeKeycloak(List<String> createdClients = []) {
        HttpServer server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        server.createContext('/realms/master/protocol/openid-connect/token', json('{"access_token":"admin-token"}'))
        server.createContext('/admin/realms/odisee/clients', new HttpHandler() {
            @Override
            void handle(HttpExchange exchange) {
                createdClients << new String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(201, -1)
                exchange.close()
            }
        })
        server.createContext('/admin/realms/odisee/users', empty(201))
        server.start()
        server
    }

    private static HttpHandler json(String body) {
        new HttpHandler() {
            @Override
            void handle(HttpExchange exchange) {
                exchange.requestBody.readAllBytes()
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8)
                exchange.responseHeaders.set('Content-Type', 'application/json')
                exchange.sendResponseHeaders(200, bytes.length)
                exchange.responseBody.withCloseable { it.write(bytes) }
            }
        }
    }

    private static HttpHandler empty(int status) {
        new HttpHandler() {
            @Override
            void handle(HttpExchange exchange) {
                exchange.requestBody.readAllBytes()
                exchange.sendResponseHeaders(status, -1)
                exchange.close()
            }
        }
    }

    private static void deleteUser(String name) {
        PathTree.delete(OdiseePath.ODISEE_VAR.resolve('user').resolve(name))
    }

}
