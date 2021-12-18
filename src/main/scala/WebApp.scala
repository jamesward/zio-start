import zhttp.http.*
import zhttp.service.*
import zio.*
import zio.stream.*

import java.io.File
import java.nio.file.Files

object WebApp extends App:

  val app = Http.collectM[Request]:
    case Method.GET -> !! =>
      def w(archetypes: ArchetypesWithLabels) =
        val content = HttpData.fromText(html.index(archetypes).toString())
        val headers = List(Header.contentTypeXhtmlXml)
        Response(Status.OK, headers, content)
      Templater.getArchetypes().fold(_ => Response(Status.INTERNAL_SERVER_ERROR), w)

    case Method.GET -> !! / "zip" / archetype / optionsString =>
      val dir = File(Files.createTempDirectory("webapp").toFile, archetype)
      val file = Files.createTempFile(archetype, ".zip").toFile
      file.delete()

      val optionsList = optionsString.split('+').toList.map:
        groupAndOption =>
          val optionKey :: optionValue :: Nil = groupAndOption.split('-').toList
          optionKey -> optionValue

      val options: Map[String, String] = optionsList.toMap

      val resp = for
        _ <- Templater.copyTemplate(dir)
        _ <- Templater.applyPatches(dir, archetype, options)
        _ <- Templater.zip(dir, file)
      yield
        val content = HttpData.fromStream(ZStream.fromFile(file.toPath))
        Response(data = content)

      resp.fold(_ => Response(Status.INTERNAL_SERVER_ERROR), identity)

  override def run(args: List[String]) =
    Server.start(8080, app).exitCode
