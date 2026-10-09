# buildcontest
Buildcontest game for minecraft spigot

Die vollständige Spielbeschreibung steht in [`rules.md`](./rules.md), die Punktetabelle in [`scoreboard-config.yml`](./scoreboard-config.yml).

## Bauen

```
mvn package
```

Die fertige Plugin-JAR liegt danach in `target/buildcontest-<version>.jar` und kann in den `plugins`-Ordner eines Spigot-Servers kopiert werden. Zum Kompilieren wird nur Internetzugriff auf das Spigot-Maven-Repository benötigt (keine lokale BuildTools-Installation); um damit tatsächlich einen Server zu **starten**, wird weiterhin eine mit `BuildTools.jar` erzeugte `spigot.jar` benötigt.
