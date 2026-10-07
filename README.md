# Odisee(R)

[www.odisee.de](http://www.odisee.de/)  
Make Your Documents Smile.

## Build, test, and package

Java 11 is required. Gradle 6.0.1 and Grails 4.0.1 do not run on Java 17 or newer.

```bash
export JAVA_HOME=/path/to/jdk-11
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
export JAVA_HOME=/path/to/jdk-11
unzip odisee-2.6-linux-x86_64.zip -d "$ODISEE_HOME"
"$ODISEE_HOME/bin/odictl" -q start
```

`odictl -q start` starts the configured LibreOffice instances and `application.jar`. The service listens on port 8080 with context path `/odisee`.

## Documentation

See [documentation/src/docs/asciidoc/Odisee.adoc](documentation/src/docs/asciidoc/Odisee.adoc). The HTML book is generated into the distribution under `docs/`.

## License

See [LICENSE](LICENSE).

## Copyright

Copyright (C) 2011-2019 art of coding UG (haftungsbeschränkt).
Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann.

Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.

## Registered Trademark

[Deutsches Patent- und Markenamt, Nr. 30 2011 047 376.](http://register.dpma.de/DPMAregister/marke/register/3020110473765/DE)
