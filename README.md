# buildcontest
Buildcontest game for minecraft spigot

Die vollständige Spielbeschreibung steht in [`rules.md`](./rules.md), die Punktetabelle in [`scoreboard-config.yml`](./scoreboard-config.yml). Praktische Anleitungen: [`ADMIN_GUIDE.md`](./ADMIN_GUIDE.md) für Server-Admins, [`PLAYER_GUIDE.md`](./PLAYER_GUIDE.md) für Spieler.

## Bauen

Benötigt JDK 25 (von Paper 26.3 vorausgesetzt, siehe `pom.xml`).

```
mvn package
```

Die fertige Plugin-JAR liegt danach in `target/buildcontest-<version>.jar` und kann in den `plugins`-Ordner eines Spigot-/Paper-Servers (Minecraft 26.3) kopiert werden. Zum Kompilieren wird nur Internetzugriff auf das Spigot-Maven-Repository benötigt (keine lokale BuildTools-Installation); um damit tatsächlich einen Server zu **starten**, wird weiterhin eine mit `BuildTools.jar` erzeugte `spigot.jar` (oder einfacher: ein Paper-26.3-Server-Jar) benötigt.

`mvn package` führt dabei auch die Unit-Tests aus (JUnit 5 + Mockito, siehe `src/test/java`); nur mit `mvn test` laufen sie ohne JAR-Bau.

## Persistenz

Gruppenmitgliedschaft, Bauplätze, gewertete Blöcke und der Spielstatus werden zusätzlich zur Minecraft-Welt in `plugins/Buildcontest/data.yml` gesichert (siehe `persistence.DataStore`) – automatisch alle `game.autosave-minutes` (Standard 5 Minuten, `config.yml`) sowie beim sauberen Server-Stopp. Nach einem Neustart lädt das Plugin diesen Stand beim Aktivieren automatisch wieder, ein laufender Contest geht also nicht verloren.
