# Odisee(R)

[www.odisee.de](http://www.odisee.de/)  
Make Your Documents Smile.

## Build, test, and package

JDK 21, Gradle 8.14.5, and Grails 7.2.4. LibreOffice UNO is `org.libreoffice:libreoffice` 26.2.2 from Maven Central (the current 26.2 still line published there).

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew build
```

`build` compiles every module, runs the unit tests, and writes:

`build/distributions/odisee-2.6-linux-x86_64.zip`

GitHub Actions uploads that zip on every push and pull request. A git tag `v*` also publishes it as a GitHub Release.

Docker image tasks are not part of `build`. They need a local Docker daemon, and the LibreOffice base images still use archived 2019 snapshots.

## Run the distribution

LibreOffice must already be installed. `etc/odiinst` points at `/usr/lib/libreoffice` by default.

```bash
export ODISEE_HOME=/opt/odisee
export JAVA_HOME=/path/to/jdk-21
unzip odisee-2.6-linux-x86_64.zip -d "$ODISEE_HOME"
"$ODISEE_HOME/bin/odictl" -q start
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
