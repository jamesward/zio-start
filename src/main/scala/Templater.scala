import scala.collection.immutable.ArraySeq
import scala.util.Try
import scala.collection.immutable.Map

type OptionGroups = Map[String, Set[String]]
type OptionGroupsWithLabels = Map[String, Set[String, String]]

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

  /*
  def getLabels(archetypes: Archetypes): ZIO[Any, IOException, ArchetypesWithLabels] =
    val all = archetypes.view.mapValues {
      optionGroups =>
        optionGroups.map {
          case (optionGroupKey, optionKey) =>
            val path = s"start/options/$optionGroupKey/$optionKey.patch"
            getClass.getClassLoader.getResourceAsStream(path)
        }
    }
  */