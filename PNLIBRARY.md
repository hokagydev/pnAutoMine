# pnLibrary

`pnAutoMine` intentionally requires `pnLibrary` at runtime.

Build API dependency:

```gradle
repositories {
    mavenCentral()
}

dependencies {
    compileOnly("io.github.pnfolder:pnlibrary-api:2.2.0-beta.2")
}
```

The dependency is declared as `depend: [WorldEdit, pnLibrary]` in `plugin.yml`.

Install the matching `pnLibrary` Bukkit/Paper plugin JAR into the server's `plugins/` directory before starting the server. Do not put it into `.paper-remapped` manually.

The plugin is built for Paper 1.21.4 / Java 21.
