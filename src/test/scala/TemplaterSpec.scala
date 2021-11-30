import zio.test.*
import zio.test.Assertion.*
import zio.test.Assertion.Render.*
import zio.test.environment.TestRandom

import java.io.IOException

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

  def spec = suite("Templater")(suite2)
