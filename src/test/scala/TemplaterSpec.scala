import zio.ZIO
import zio.test.*
import zio.test.Assertion.*
import zio.test.Assertion.Render.*
import zio.test.environment.TestRandom

import java.nio.file.Files
import java.io.{File, IOException}

object TemplaterSpec extends DefaultRunnableSpec:
  val test1 = test("fail when archetypes dir does not exist"):
    val paths = Set("foo", "options/foo/bar")
    val result = Templater.parseTree(paths)
    assert(result)(isLeft)

  val test2 = test("fail when archetypes is empty"):
    val paths = Set("foo", "options/foo/bar", "archetypes/")
    val result = Templater.parseTree(paths)
    assert(result)(isLeft)

  val test3 = test("fail when options dir does not exist"):
    val paths = Set("foo", "archetypes/foo", "foo/bar/baz")
    val result = Templater.parseTree(paths)
    assert(result)(isLeft)

  val test4 = test("fail when options is empty"):
    val paths = Set("foo", "archetypes/foo", "options/", "foo/bar/baz")
    val result = Templater.parseTree(paths)
    assert(result)(isLeft)

  val test5 = test("fail when options do not exist for archetype"):
    val paths = Set("archetypes/atype/options/anoptiongroup-anoption.patch", "options/asdf/foo.patch")
    val result = Templater.parseTree(paths)
    assert(result)(isLeft)

  val test6 = test("work when an option for an archetype exists"):
    val paths = Set("archetypes/atype/options/anoptiongroup-anoption.patch", "options/anoptiongroup/anoption.patch")
    val result = Templater.parseTree(paths)
    val expected = Map(
      "atype" -> Map("anoptiongroup" -> Set("anoption"))
    )
    assert(result)(isRight(equalTo(expected)))

  val test7 = test("work with multiple option groups"):
    val paths = Set(
      "archetypes/atype/options/anoptiongroup-anoption.patch",
      "archetypes/atype/options/anotheroptiongroup-anoption.patch",
      "options/anoptiongroup/anoption.patch",
      "options/anotheroptiongroup/anoption.patch",
    )
    val result = Templater.parseTree(paths)
    val expected = Map(
      "atype" -> Map("anoptiongroup" -> Set("anoption"), "anotheroptiongroup" -> Set("anoption"))
    )
    assert(result)(isRight(equalTo(expected)))

  val test8 = test("work with multiple options"):
    val paths = Set(
      "archetypes/atype/options/anoptiongroup-anoption.patch",
      "archetypes/atype/options/anoptiongroup-anotheroption.patch",
      "options/anoptiongroup/anoption.patch",
      "options/anoptiongroup/anotheroption.patch",
    )
    val result = Templater.parseTree(paths)
    val expected = Map(
      "atype" -> Map("anoptiongroup" -> Set("anoption", "anotheroption"))
    )
    assert(result)(isRight(equalTo(expected)))

  val suite1 = suite("parseTree")(test1, test2, test3, test4, test5, test6, test7, test8)

  val s2t1 = testM("work when labels exist"):
    val paths = Set(
      "archetypes/atype/options/anoptiongroup-anoption.patch",
      "archetypes/atype/options/anoptiongroup-anotheroption.patch",
      "options/anoptiongroup/anoption.patch",
      "options/anoptiongroup/anotheroption.patch",
    )
    val tree = Templater.parseTree(paths)
    val expected = Map(
      "atype" -> Map("anoptiongroup" -> Set("anoption" -> "An Option", "anotheroption" -> "Another Option"))
    )

    for
      treeWithLabels <- Templater.getLabels(tree.toOption.get)
    yield
      assert(treeWithLabels)(equalTo(expected))

  val s2t2 = testM("fail when the option does not exist"):
    val paths = Set(
      "archetypes/atype/options/anoptiongroup-foo.patch",
      "options/anoptiongroup/foo.patch",
    )
    val tree = Templater.parseTree(paths)

    assertM(Templater.getLabels(tree.toOption.get).run)(fails(isSubtype[Templater.CouldNotReadLabel](anything)))

  val suite2 = suite("getLabels")(s2t1, s2t2)


  val s3t1 = testM("work"):
    // todo: the classpath should really be an environment
    val expected = Map(
      "cli" -> Map(
        "scala" -> Set("3" -> "Scala 3", "2_13" -> "Scala 2.13"),
        "zio" -> Set("2" -> "ZIO 2", "1" -> "ZIO 1"),
      ),
      "foo" -> Map(
        "anoptiongroup" -> Set("anoption" -> "An Option")
      ),
      "rest" -> Map(
        "scala" -> Set("3" -> "Scala 3"),
        "zio" -> Set("1" -> "ZIO 1"),
      )
    )

    for
      archetypes <- Templater.getArchetypes()
    yield assert(archetypes)(equalTo(expected))

  val suite3 = suite("getArchetypes")(s3t1)


  // todo: manage tmp dir resource and clean it up
  val s4t1 = testM("work"):
    val f = Files.createTempDirectory("templater").toFile

    // todo: classpath environment
    for
      _ <- Templater.copyTemplate(f)
    yield
      // todo: test file permissions
      val nf = File(f, "build.sbt")
      assert(nf.exists())(isTrue)

  val suite4 = suite("copyTemplate")(s4t1)


  // todo: manage tmp dir resource and clean it up
  val s5t1 = testM("work"):
    val f = Files.createTempDirectory("templater").toFile

    for
      _ <- Templater.copyTemplate(f)
      _ <- Templater.applyPatches(f, "foo", Map("anoptiongroup" -> "anoption"))
    yield
      // todo: test file contents
      val tf = File(f, "anoptiongroup-anoption.txt")
      assert(tf.exists())(isTrue)
      val of = File(f, "anoption.txt")
      assert(of.exists())(isTrue)
      val ff = File(f, "foo.txt")
      assert(ff.exists())(isTrue)

  val suite5 = suite("applyPatches")(s5t1)


  val s6t1 = testM("work"):
    val dir = Files.createTempDirectory("templater").toFile
    val zip = Files.createTempFile("templater", ".zip").toFile
    zip.delete() // zip creates it and we just use createTempFile to get a unique name (ie I'm lazy)

    for
      _ <- Templater.copyTemplate(dir)
      _ <- Templater.zip(dir, zip)
    yield
      // todo: test zip contents
      assert(zip.exists())(isTrue)

  val suite6 = suite("zip")(s6t1)

  def spec = suite("Templater")(suite1, suite2, suite3, suite4, suite5, suite6)
