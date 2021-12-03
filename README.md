ZIO Start
---------

A little webapp to start new ZIO projects. Inspired by Typesafe Activator, start.spring.io, etc.


## TODO
- Archetype Labels
- Post-Download Instructions
- GitHub Repo Create
- Cloud Deploy
- Integration test all of the architype option combos.
- Generate the matrix of projects at build time?
- Run tests in CI
- GraalVMify
- If an option has only 1 option, select or don't show it?
- Test cleanup
- Cache zips

## Notes
- `src/main/resources/start/options/*/*.patch` must have a first line that is the label for the option in the UI
- The options today are flattened so if an archetype supports scala 3 + zio 1, the following directory must exist:
  `src/main/resources/start/archetypes/*/scala-3/zio-1`
  If a third option is added at some point the matrix gets much bigger and the new option might not be related to others so having a dir structure like the following might make sense:
  `src/main/resources/start/archetypes/*/scala-3/zio-1`
  `src/main/resources/start/archetypes/*/foo-asdf`

## Dev Info

```
./sbt test
```

```
./sbt run
```

[localhost:8080/](http://localhost:8080/)