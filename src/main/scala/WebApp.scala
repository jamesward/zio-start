import zhttp.http.*
import zhttp.http.Response.HttpResponse
import zhttp.service.*
import zio.*
import zio.duration.durationInt
import zio.system.env
import zio.stream.*
import io.netty.handler.codec.http.{HttpHeaderNames as JHttpHeaderNames, HttpHeaderValues as JHttpHeaderValues}
import io.netty.util.CharsetUtil
import zhttp.core.{JDefaultHttpHeaders, JHttpHeaders}

import java.io.{File, IOException}
import java.nio.file.Files

object WebApp extends App:

  val app = Http.collectM[Request]:
    case Method.GET -> Root =>
      def w(archetypes: ArchetypesWithLabels) =
        val content = HttpData.CompleteData(Chunk.fromArray(html.index(archetypes).body.getBytes(HTTP_CHARSET)))
        val headers = List(Header.custom(JHttpHeaderNames.CONTENT_TYPE.toString, JHttpHeaderValues.TEXT_HTML.toString))
        Response.http(Status.OK, headers, content)
      Templater.getArchetypes().fold(_ => Response.http(Status.INTERNAL_SERVER_ERROR), w)

    case Method.GET -> Root / "zip" / archetype / optionsString =>
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
        Response.http(content = content)

      resp.fold(_ => Response.http(Status.INTERNAL_SERVER_ERROR), identity)

  override def run(args: List[String]) =
    Server.start(8080, app).exitCode
