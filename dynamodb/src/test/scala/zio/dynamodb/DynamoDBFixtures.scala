package zio.dynamodb

import zio.dynamodb.DynamoDBQuery.{ getItem, DeleteItem, GetItem, PutItem }

//noinspection TypeAnnotation
trait DynamoDBFixtures {
  val tableName1            = TableName("T1")
  val tableName2            = TableName("T2")
  val tableName3            = TableName("T3")
  def createGetItem(i: Int) = getItem("T1", PrimaryKey(s"k$i" -> s"v$i"))

  val primaryKeyT1   = PrimaryKey("k1" -> "v1")
  val primaryKeyT1_2 = PrimaryKey("k1" -> "v2")
  val primaryKeyT2   = PrimaryKey("k2" -> "v2")
  val primaryKeyT3   = PrimaryKey("k3" -> "v3")
  val primaryKeyT3_2 = PrimaryKey("k3" -> "v4")

  val getItemT1              =
    GetItem(key = primaryKeyT1, tableName = tableName1)
  val getItemT1_2            = GetItem(key = primaryKeyT1_2, tableName = tableName1)
  val getItemT1_NotExists    = GetItem(key = PrimaryKey("k1" -> "NOT_EXISTS"), tableName = tableName1)
  val getItemT3              = GetItem(key = primaryKeyT3, tableName = tableName3)
  val getItemT3_2            = GetItem(key = primaryKeyT3_2, tableName = tableName3)
  val itemT1: Item           = getItemT1.key
  val itemT1_NotExists: Item = getItemT1_NotExists.key
  val itemT1_2: Item         = getItemT1_2.key
  val itemT3: Item           = getItemT3.key
  val itemT3_2: Item         = getItemT3_2.key

  val putItem1     = PutItem(tableName = tableName1, item = primaryKeyT1)
  val putItem1_2   = PutItem(tableName = tableName1, item = primaryKeyT1_2)
  val putItem3_2   = PutItem(tableName = tableName3, item = primaryKeyT3_2)
  val deleteItemT1 = DeleteItem(tableName = tableName1, key = primaryKeyT1)
  val deleteItemT3 = DeleteItem(tableName = tableName3, key = primaryKeyT3)

}