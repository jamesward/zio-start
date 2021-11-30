import zhttp.http.*
import zhttp.http.Response.HttpResponse
import zhttp.service.{ChannelFactory, Client, EventLoopGroup, Server}
import zio.*
import zio.duration.durationInt
import zio.system.env
import zio.stream.*

import java.io.IOException

object WebApp extends App:

  val app = Http.collect[Request]:
    case Method.GET -> Root =>
      Response.text("hello, world")

  override def run(args: List[String]) =
    Server.start(8080, app).exitCode
