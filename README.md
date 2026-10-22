# Odisee(R)

[www.odisee.de](http://www.odisee.de/)  
Make Your Documents Smile.

## Build, test, and package

Run `make help` for the local stack, the tests, and a GitHub image.

JDK 21, Gradle 8.14.5, and Grails 7.2.4. LibreOffice UNO is `org.libreoffice:libreoffice` 26.2.2 from Maven Central (the current 26.2 still line published there).

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew build
```

`build` compiles every module, runs the unit tests, and writes:

`build/distributions/odisee-2.6.0-linux-x86_64.zip`

The zip contains `application.jar`, `bin/odictl`, `etc/`, the Java client, the LibreOffice extension, and the HTML documentation.

GitHub Actions splits this into jobs `setup`, `build`, `test`, and `package` in `.github/workflows/build.yml`. `test` and `image` are reusable workflows. Shared steps are `.github/actions/prepare`. `setup` checks out the sources, installs JDK 21, and fills the Gradle cache. `build` compiles and assembles, does not run tests, and uploads those outputs. `test` downloads the outputs and runs the unit tests that do not start a container. `package` downloads the outputs, writes the zip, and uploads it. A git tag `v*` publishes that zip as a GitHub Release. Job `java-client-office` runs the LibreOffice proof.

Podman tasks are not part of `build`. They need a local Podman service. `:webservice:startStack` builds the service image (Ubuntu 26.04, OpenJDK 21, headless LibreOffice Writer) and starts `odisee`, `minio`, and `keycloak` with `podman compose` from `webservice/src/main/container/compose.yaml`. Odisee listens on port 8080, MinIO on 9000, and Keycloak on 8180. `:webservice:libreOfficeTest` builds the test image at `webservice/src/test/container/libreoffice`. `:webservice:minioTest` starts a throwaway MinIO and stores one object. `:webservice:test` does not start a container.

The version is semantic. A local build is `2.6.0`. GitHub Actions sets `ODISEE_BUILD_NUMBER` to the workflow run number, so that build is `2.6.0-<run>`, for example `2.6.0-42`. The zip, the uploaded artifact, and the image tag use that version.

GitHub Actions runs on `ubuntu-26.04`. Job `image` publishes that service image to `ghcr.io/rbe/odisee26` on `master`, on a `v*` tag, and when someone runs the workflow by hand. One manifest lists `linux/amd64` and `linux/arm64`. There is no `darwin` image. The LibreOffice layer stays cached; `application.jar` is copied after it. `master` and a `v*` tag push `2.6.0-<run>` and `latest`. A `v*` tag also pushes that tag name. A manual run of another branch pushes only `2.6.0-<run>`. Pull requests do not publish. The first publish is private until the package is set public.

```bash
podman pull ghcr.io/rbe/odisee26:2.6.0-<run>
```

## Proxy

An HTTP proxy is optional.

Podman reads the environment:

```text
HTTP_PROXY=http://proxy.example.com:8888
HTTPS_PROXY=http://proxy.example.com:8888
NO_PROXY=localhost,example.com
```

Gradle reads `$HOME/.gradle/gradle.properties`:

```text
systemProp.http.proxyHost=proxy.example.com
systemProp.http.proxyPort=8888
systemProp.http.nonProxyHosts=localhost|127.0.0.1|example.com
systemProp.https.proxyHost=proxy.example.com
systemProp.https.proxyPort=8888
systemProp.https.nonProxyHosts=localhost|127.0.0.1|example.com
```

## Run the distribution

LibreOffice must already be installed. `etc/odiinst` points at `/usr/lib/libreoffice` by default. `java` is on `PATH`. `odictl` derives its Java home from that `java` when it runs.

`ODISEE_HOME` defaults to `$HOME/.local/share/odisee`. Set `ODISEE_HOME` when the install lives somewhere else (`/opt/odisee`, the container, a systemd unit). A user install links `odictl` into `$HOME/.local/bin`. The service image puts that command on `PATH` as `/home/odisee/.local/bin` in the image environment.

```bash
mkdir -p "${ODISEE_HOME:-$HOME/.local/share/odisee}" "$HOME/.local/bin"
unzip odisee-2.6.0-linux-x86_64.zip -d "${ODISEE_HOME:-$HOME/.local/share/odisee}"
ln -s "${ODISEE_HOME:-$HOME/.local/share/odisee}/bin/odictl" "$HOME/.local/bin/odictl"
odictl -q start
```

`odictl -q start` starts the configured LibreOffice instances and `application.jar`. The service listens on port 8080 with context path `/odisee`.

## Documentation

See [documentation/src/docs/asciidoc/Odisee.adoc](documentation/src/docs/asciidoc/Odisee.adoc). The HTML book is generated into the distribution under `docs/`.

`POST /document/generate` accepts XML (`text/xml`, the default) and JSON (`application/json`).
The JSON shape is `webservice/src/main/resources/json/request.schema.json`.
Java, PHP, VB.NET, and Oracle clients can send JSON via `useJson()` / `use_json`.

## License

See [LICENSE](LICENSE).

## Copyright

Copyright (C) 2011-2019 art of coding UG (haftungsbeschränkt).
Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann.

Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.

## Registered Trademark

[Deutsches Patent- und Markenamt, Nr. 30 2011 047 376.](http://register.dpma.de/DPMAregister/marke/register/3020110473765/DE)
