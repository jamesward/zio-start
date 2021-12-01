ZIO Start
---------

```
./sbt test
```

```
./sbt run
```

[localhost:8080/](http://localhost:8080/)

```
/archetype/options.zip
/archetype/options.git
```

`/`
Page with archetypes & options UI

Actions:
`Download` downloads zip, display next steps
`Save on GitHub` OAuth to GitHub, create repo, store files, display next steps
`Deploy to Cloud` Cloud Run for now - Cloud Run Button with git repo


TODO
- Integration test all of the architype option combos.
- Generate the matrix of projects at build time?

Notes
- `src/main/resources/start/options/*/*.patch` must have a first line that is the label for the option in the UI
- The options today are flattened so if an archetype supports scala 3 + zio 1, the following directory must exist:
  `src/main/resources/start/archetypes/*/scala-3/zio-1`
  If a third option is added at some point the matrix gets much bigger and the new option might not be related to others so having a dir structure like the following might make sense:
  `src/main/resources/start/archetypes/*/scala-3/zio-1`
  `src/main/resources/start/archetypes/*/foo-asdf`

Dev Info
