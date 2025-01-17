package zio.dynamodb.codec

import zio.dynamodb._
import zio.dynamodb.codec.Invoice.PreBilled
import zio.test.Assertion._
import zio.test._

import java.time.Instant
import scala.collection.immutable.ListMap
import zio.test.ZIOSpecDefault
import scala.util.Try

object ItemEncoderSpec extends ZIOSpecDefault with CodecTestFixtures {
  override def spec = suite("ItemEncoder Suite")(mainSuite)

  val mainSuite = suite("Main Suite")(
    test("encodes generic record") {

      val av = Codec.encoder(recordSchema)(ListMap("foo" -> "FOO", "bar" -> 1))

      assert(av)(
        equalTo(AttributeValue.Map(Map(toAvString("foo") -> toAvString("FOO"), toAvString("bar") -> toAvNum(1))))
      )
    },
    test("encodes enumeration") {

      val av = Codec.encoder(enumSchema)("FOO")

      assert(av)(
        equalTo(AttributeValue.Map(Map(toAvString("string") -> toAvString("FOO"))))
      )
    },
    test("encodes List of Int") {
      val expectedItem: Item = Item("nums" -> List(1, 2, 3))

      val item = DynamoDBQuery.toItem(CaseClassOfList(List(1, 2, 3)))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes simple Optional Item") {
      val expectedItem: Item = Item("id" -> 2, "name" -> "Avi")

      val item = DynamoDBQuery.toItem(SimpleCaseClass3Option(2, "Avi", opt = None))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes nested Optional Item") {
      val expectedItem: Item = Item("opt" -> 1)

      val item = DynamoDBQuery.toItem(CaseClassOfNestedOption(opt = Some(Some(1))))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes simple Item") {
      val expectedItem: Item = Item("id" -> 2, "name" -> "Avi", "flag" -> true)

      val item = DynamoDBQuery.toItem(SimpleCaseClass3(2, "Avi", flag = true))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes nested Items") {
      val expectedItem: Item = Item("id" -> 1, "nested" -> Item("id" -> 2, "name" -> "Avi", "flag" -> true))

      val item = DynamoDBQuery.toItem(NestedCaseClass2(1, SimpleCaseClass3(2, "Avi", flag = true)))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes LocalDateTime") {
      val expectedItem: Item = Item("instant" -> "2021-09-28T00:00:00Z")

      val item =
        DynamoDBQuery.toItem(CaseClassOfInstant(Instant.parse("2021-09-28T00:00:00Z")))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes Ok ADT") {
      val expectedItem: Item = Item("status" -> Item("Ok" -> Item("response" -> List("1", "2"))))

      val item = DynamoDBQuery.toItem(CaseClassOfStatus(Ok(List("1", "2"))))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes Pending case object ADT") {
      val expectedItem: Item = Item("status" -> Item("Pending" -> null))

      val item = DynamoDBQuery.toItem(CaseClassOfStatus(Pending))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes tuple3") {
      val expected: Item = new AttrMap(Map("tuple" -> toAvList(toAvTuple(1, 2), toAvNum(3))))

      val item = DynamoDBQuery.toItem(CaseClassOfTuple3((1, 2, 3)))

      assert(item)(equalTo(expected))
    },
    test("encodes map") {
      val expectedItem: Item = Item("map" -> Map("One" -> 1, "Two" -> 2))

      val item = DynamoDBQuery.toItem(CaseClassOfMapOfInt(Map("One" -> 1, "Two" -> 2)))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes Either Right") {
      val expectedItem: Item = Item("either" -> Item("Right" -> 1))

      val item = DynamoDBQuery.toItem(CaseClassOfEither(Right(1)))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes Either Left") {
      val expectedItem: Item = Item("either" -> Item("Left" -> "boom"))

      val item = DynamoDBQuery.toItem(CaseClassOfEither(Left("boom")))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes List of case class") {
      val expectedItem: Item = Item("elements" -> List(Item("id" -> 1, "name" -> "Avi", "flag" -> true)))

      val item = DynamoDBQuery.toItem(CaseClassOfListOfCaseClass(List(SimpleCaseClass3(1, "Avi", flag = true))))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes enum with @discriminatorName annotation") {
      val expectedItem: Item =
        Item(
          Map(
            "enum" -> AttributeValue.Map(
              Map(
                AttributeValue.String("value")              -> AttributeValue.String("foobar"),
                AttributeValue.String("funkyDiscriminator") -> AttributeValue.String("StringValue")
              )
            )
          )
        )

      val item = DynamoDBQuery.toItem(WithDiscriminatedEnum(WithDiscriminatedEnum.StringValue("foobar")))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes enum with @discriminatorName annotation and @fieldName annotation for a case class") {
      val expectedItem: Item =
        Item(
          Map(
            "enum" -> AttributeValue.Map(
              Map(
                AttributeValue.String("funky_field_name")   -> AttributeValue.String("foobar"),
                AttributeValue.String("funkyDiscriminator") -> AttributeValue.String("StringValue2")
              )
            )
          )
        )

      val item = DynamoDBQuery.toItem(WithDiscriminatedEnum(WithDiscriminatedEnum.StringValue2("foobar")))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes enum with @discriminatorName annotation and an @caseName annotation on a case class") {
      val expectedItem: Item =
        Item(
          Map(
            "enum" -> AttributeValue.Map(
              Map(
                AttributeValue.String("value")              -> AttributeValue.Number(BigDecimal(1)),
                AttributeValue.String("funkyDiscriminator") -> AttributeValue.String("ival")
              )
            )
          )
        )

      val item = DynamoDBQuery.toItem(WithDiscriminatedEnum(WithDiscriminatedEnum.IntValue(1)))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes enum with @discriminatorName annotation and case object as item without a @caseName annotation") {
      val expectedItem: Item =
        Item(
          Map(
            "enum" -> AttributeValue.Map(
              Map(
                AttributeValue.String("funkyDiscriminator") -> AttributeValue.String("ONE")
              )
            )
          )
        )

      val item = DynamoDBQuery.toItem(WithDiscriminatedEnum(WithDiscriminatedEnum.ONE))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes enum with @discriminatorName annotation and case object as item with @caseName annotation of '2'") {
      val expectedItem: Item =
        Item(
          Map(
            "enum" -> AttributeValue.Map(
              Map(
                AttributeValue.String("funkyDiscriminator") -> AttributeValue.String("2")
              )
            )
          )
        )

      val item = DynamoDBQuery.toItem(WithDiscriminatedEnum(WithDiscriminatedEnum.TWO))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes top level enum with @discriminatorName annotation") {
      val expectedItem: Item =
        Item(
          Map(
            "id"                 -> AttributeValue.Number(BigDecimal(1)),
            "s"                  -> AttributeValue.String("foobar"),
            "funkyDiscriminator" -> AttributeValue.String("PreBilled")
          )
        )

      val item = DynamoDBQuery.toItem[Invoice](PreBilled(1, "foobar"))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes case object only enum with @enumOfCaseObjects annotation") {
      val expectedItem: Item = Item(Map("enum" -> AttributeValue.String("ONE")))

      val item = DynamoDBQuery.toItem(WithCaseObjectOnlyEnum(WithCaseObjectOnlyEnum.ONE))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes case object only enum with @enumOfCaseObjects annotation and @caseName annotation of '2'") {
      val expectedItem: Item = Item(Map("enum" -> AttributeValue.String("2")))

      val item = DynamoDBQuery.toItem(WithCaseObjectOnlyEnum(WithCaseObjectOnlyEnum.TWO))

      assert(item)(equalTo(expectedItem))
    },
    test("fails encoding of enum with @enumOfCaseObjects annotation that does not have all case objects") {

      val item = Try(DynamoDBQuery.toItem(WithCaseObjectOnlyEnum2(WithCaseObjectOnlyEnum2.ONE)))

      assert(item)(
        isFailure(
          hasMessage(
            equalTo(
              "Can not encode enum ONE - @enumOfCaseObjects annotation present when all instances are not case objects."
            )
          )
        )
      )
    },
    test("encodes enum and honours @caseName annotation when there is no @enumOfCaseObjects annotation") {
      val expectedItem: Item = Item("enum" -> Item(Map("1" -> AttributeValue.Null)))

      val item = DynamoDBQuery.toItem(WithEnumWithoutDiscriminator(WithEnumWithoutDiscriminator.ONE))

      assert(item)(equalTo(expectedItem))
    },
    test("encodes enum without @discriminatorName annotation and uses @fieldName field level annotation") {
      val expectedItem: Item = Item(
        Map(
          "enum" -> AttributeValue.Map(
            Map(
              AttributeValue.String("Three") -> AttributeValue.Map(
                Map(AttributeValue.String("funky_field_name") -> AttributeValue.String("value"))
              )
            )
          )
        )
      )

      val item = DynamoDBQuery.toItem(WithEnumWithoutDiscriminator(WithEnumWithoutDiscriminator.Three(value = "value")))

      assert(item)(equalTo(expectedItem))
    }
  )

}
