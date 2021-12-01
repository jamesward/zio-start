import io.github.classgraph.ClassGraph
import zio.ZIO
import zio.blocking.Blocking
import zio.process.{Command, ProcessInput, CommandError}
import zio.stream.{ZSink, ZStream, ZTransducer}

import java.io.{File, IOException}
import scala.collection.MapView
import scala.collection.immutable.{ArraySeq, Map}
import scala.jdk.CollectionConverters.*
import scala.util.Try

// todo: opaques
type OptionGroups = Map[String, Set[String]]
type OptionGroupsWithLabels = Map[String, Set[(String, String)]]

type Archetypes = Map[String, OptionGroups]
type ArchetypesWithLabels = Map[String, OptionGroupsWithLabels]

case object NoArchetypes
case class MissingOption(groupKey: String, optionKey: String)

object Templater:

  // turns a dir stucture into a set of archetypes
  def parseTree(paths: Set[String]): Either[NoArchetypes.type | Map[String, Set[MissingOption]], Archetypes] =
    val pathed = paths.map:
      path =>
        path.split('/').toList // note that toSeq doesn't work because it is an ArraySeq which silently does not match on the collect extractor

    val maybeArchetypesOptions: Set[(String, String)] = pathed.collect:
      case "archetypes" :: key :: "options" :: file :: Nil =>
        key -> file

    def updatedWithFold(s: Map[String, Set[String]], kv: (String, String)): Map[String, Set[String]] =
      s.updatedWith(kv._1):
        maybeValues =>
          maybeValues.orElse(Some(Set.empty)).map(_ + kv._2)

    val archetypesWithFiles: Map[String, Set[String]] = maybeArchetypesOptions.foldLeft(Map.empty)(updatedWithFold)

    if archetypesWithFiles.isEmpty then
      Left(NoArchetypes)
    else
      val maybeOptions = pathed.collect:
        case "options" :: groupKey :: file :: Nil =>
          groupKey -> file

      val options: OptionGroups = maybeOptions.foldLeft(Map.empty):
        case (existing, (groupKey, file)) =>
          val optionKey = file.stripSuffix(".patch")
          updatedWithFold(existing, (groupKey, optionKey))

      // todo: some yuck
      val archetypesWithOptions: Map[String, (Set[MissingOption], Set[(String, String)])] = archetypesWithFiles.map:
        case (archetypeKey, archetypeOptionsFiles) =>
          val optionsOrMissing = archetypeOptionsFiles.flatMap:
            archetypeOptionsFile =>
              val archetypeOptions = archetypeOptionsFile.stripSuffix(".patch").split('+').toSet
              archetypeOptions.map:
                archetypeOption =>
                  val optionGroupKey :: optionKey :: Nil = archetypeOption.split("-").toList
                  // validate the optionGroup and option exist
                  val maybeOptionGroup = options.get(optionGroupKey).flatMap:
                    optionGroupOptions =>
                      optionGroupOptions.find(_ == optionKey).map(_ => (optionGroupKey, optionKey))

                  maybeOptionGroup.toRight(MissingOption(optionGroupKey, optionKey))

          archetypeKey -> optionsOrMissing.partitionMap(identity)

      // todo: probably a better way to split these out
      val missingOptions: Map[String, Set[MissingOption]] =
        archetypesWithOptions.collect:
          case (archetypeKey, (missingOptions, _)) if missingOptions.nonEmpty =>
            archetypeKey -> missingOptions

      val archetypesWithOptionGroups: Archetypes =
        archetypesWithOptions.collect:
          case (archetypeKey, (_, optionGroups)) if optionGroups.nonEmpty =>
            archetypeKey -> optionGroups.foldLeft(Map.empty[String, Set[String]])(updatedWithFold)

      Either.cond(missingOptions.values.flatten.isEmpty, archetypesWithOptionGroups, missingOptions)

  case class CouldNotReadLabel(optionGroupKey: String, optionKey: String)

  // todo: classpath from environment?
  def getLabels(archetypes: Archetypes): ZIO[Blocking, IOException | CouldNotReadLabel, ArchetypesWithLabels] =
    val all = archetypes.view.mapValues:
        optionGroups =>
          ZIO.foreach(optionGroups):
            case (optionGroupKey, optionKeys) =>
              val optionsWithLabels: ZIO[Blocking, IOException | CouldNotReadLabel, Set[(String, String)]] = ZIO.foreach(optionKeys):
                optionKey =>
                  val path = s"start/options/$optionGroupKey/$optionKey.patch"
                  val is = getClass.getClassLoader.getResourceAsStream(path)
                  if is == null then
                    ZIO.fail(CouldNotReadLabel(optionGroupKey, optionKey))
                  else
                    val firstLineStream = ZStream.fromInputStream(is).transduce(ZTransducer.utf8Decode >>> ZTransducer.splitLines).take(1)
                    firstLineStream.runLast.flatMap:
                      maybeLabel =>
                        maybeLabel.fold(ZIO.fail(CouldNotReadLabel(optionGroupKey, optionKey)))(label => ZIO.succeed(optionKey -> label))

              optionsWithLabels.map(optionGroupKey -> _)

    ZIO.foreach(all.toMap):
      case (archetypeKey, optionsWithLabels) =>
        optionsWithLabels.map:
          archetypeKey -> _

  // todo: classpath from environment
  def getArchetypes(): ZIO[Blocking, IOException | CouldNotReadLabel | NoArchetypes.type | Map[String, Set[MissingOption]], ArchetypesWithLabels] =

    // todo: can throw
    val scanResult = ClassGraph().acceptPaths("start").scan()

    val files = scanResult.getAllResources.getPathsRelativeToClasspathElement.asScala.map(_.stripPrefix("start/"))

    // todo: why does inference fail on fromEither?
    for
      tree <- ZIO.fromEither[NoArchetypes.type | Map[String, Set[MissingOption]], Archetypes](parseTree(files.toSet))
      archetypes <- getLabels(tree)
    yield archetypes

  case class DoesNotExist(dir: File)

  def copyTemplate(destination: File): ZIO[Blocking, DoesNotExist | Throwable, Unit] =
    // todo: can throw
    val scanResult = ClassGraph().acceptPaths("start/template").scan()
    val files = scanResult.getAllResources.getPathsRelativeToClasspathElement.asScala

    val copies = ZIO.foreach(files):
      file =>
        val is = getClass.getClassLoader.getResourceAsStream(file)
        // todo: inference issue
        val write: ZIO[Blocking, DoesNotExist | Throwable, Long] =
          if is == null then
            ZIO.fail(DoesNotExist(File(file)))
          else
            val dest = File(destination, file.stripPrefix("start/template"))
            dest.getParentFile.mkdirs()
            for
              result <- ZStream.fromInputStream(is).run(ZSink.fromFile(dest.toPath))
            yield
              dest.setExecutable(true) // todo
              result
        write

    copies.unit


  case class ReadError()

  def applyPatches(dir: File, archetypeKey: String, options: Map[String, String]): ZIO[Blocking, DoesNotExist | Exception, Unit] =
    println(dir)
    if !dir.exists() then
      ZIO.fail(DoesNotExist(dir))
    else
      val stringifiedOptionsList = options.map:
        case (optionGroupKey, optionKey) =>
          s"$optionGroupKey-$optionKey"

      val stringifiedOptions = stringifiedOptionsList.mkString("+")

      val archetypePatchFile = s"start/archetypes/$archetypeKey/options/$stringifiedOptions.patch"

      val optionPatchFiles = options.map:
        case (optionGroupKey, optionKey) =>
          s"start/options/$optionGroupKey/$optionKey.patch"

      val patchFiles = optionPatchFiles.toSeq :+ archetypePatchFile

      val patches = ZIO.foreach(patchFiles):
        path =>
          val is = getClass.getClassLoader.getResourceAsStream(path)
          // todo: is null handling
          for
            maybeIn <- ZStream.fromInputStream(is).transduce(ZTransducer.utf8Decode).runLast
            in = maybeIn.get // todo
            process <- Command("patch", "-p0")
                   .workingDirectory(dir)
                   .stdin(ProcessInput.fromUTF8String(in)) // todo: fromStream
                   .run
            //err <- process.stderr.string // todo: propagate err & out to the error channel on error
            //out <- process.stdout.string
            _ <- process.successfulExitCode
          yield
            //println(err)
            //println(out)
            ()
      patches.unit
