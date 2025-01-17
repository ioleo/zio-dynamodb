package zio.dynamodb

import zio.dynamodb.proofs.Sizable

/* https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.OperatorsAndFunctions.html

In the following syntax summary, an operand can be the following:
1) A top-level attribute name, such as Id, Title, Description, or ProductCategory
2) A document path that references a nested attribute
ALso, my observation is that operand can be an AttributeValue


condition-expression ::=
      operand comparator operand
    | operand BETWEEN operand AND operand
    | operand IN ( operand (',' operand (, ...) ))
    | function
    | condition AND condition
    | condition OR condition
    | NOT condition
    | ( condition )

comparator ::=
    =
    | <>
    | <
    | <=
    | >
    | >=

function ::=
    attribute_exists (path)
    | attribute_not_exists (path)
    | attribute_type (path, type)
    | begins_with (path, substr)
    | contains (path, operand)
    | size (path)
 */

sealed trait ConditionExpression[-From] extends Renderable { self =>
  import ConditionExpression._

  def &&[From1 <: From](that: ConditionExpression[From1]): ConditionExpression[From1] =
    And(self, that)
  def ||[From1 <: From](that: ConditionExpression[From1]): ConditionExpression[From1] =
    Or(self, that)
  def unary_![From1 <: From]: ConditionExpression[From1]                              = Not(self)

  def render: AliasMapRender[String] =
    self match {
      case between: Between[_]         =>
        AliasMapRender.getOrInsert(between.minValue)
        for {
          l   <- between.left.render
          min <- AliasMapRender.getOrInsert(between.minValue)
          max <- AliasMapRender.getOrInsert(between.maxValue)
        } yield s"$l BETWEEN $min AND $max"
      case in: In[_]                   =>
        for {
          l    <- in.left.render
          vals <- in.values
                    .foldLeft(AliasMapRender.empty.map(_ => "")) {
                      case (acc, value) =>
                        acc.zipWith(AliasMapRender.getOrInsert(value)) {
                          case (acc, action) =>
                            if (acc.isEmpty) action
                            else s"$acc, $action"
                        }
                    }
        } yield s"$l IN ($vals)"
      case ae: AttributeExists[_]      => AliasMapRender.succeed(s"attribute_exists(${ae.path})")
      case ane: AttributeNotExists[_]  => AliasMapRender.succeed(s"attribute_not_exists(${ane.path})")
      case at: AttributeType[_]        =>
        at.attributeType.render.map(v => s"attribute_type(${at.path}, $v)")
      case c: Contains[_]              => AliasMapRender.getOrInsert(c.value).map(v => s"contains(${c.path}, $v)")
      case bw: BeginsWith[_]           =>
        AliasMapRender.getOrInsert(bw.value).map(v => s"begins_with(${bw.path}, $v)")
      case and: And[_]                 => and.left.render.zipWith(and.right.render) { case (l, r) => s"($l) AND ($r)" }
      case or: Or[_]                   => or.left.render.zipWith(or.right.render) { case (l, r) => s"($l) OR ($r)" }
      case not: Not[_]                 => not.exprn.render.map(v => s"NOT ($v)")
      case eq: Equals[_]               => eq.left.render.zipWith(eq.right.render) { case (l, r) => s"($l) = ($r)" }
      case neq: NotEqual[_]            =>
        neq.left.render.zipWith(neq.right.render) { case (l, r) => s"($l) <> ($r)" }
      case lt: LessThan[_]             => lt.left.render.zipWith(lt.right.render) { case (l, r) => s"($l) < ($r)" }
      case gt: GreaterThan[_]          =>
        gt.left.render.zipWith(gt.right.render) { case (l, r) => s"($l) > ($r)" }
      case lteq: LessThanOrEqual[_]    =>
        lteq.left.render.zipWith(lteq.right.render) { case (l, r) => s"($l) <= ($r)" }
      case gteq: GreaterThanOrEqual[_] =>
        gteq.left.render.zipWith(gteq.right.render) { case (l, r) => s"($l) >= ($r)" }
    }

}

// BNF  https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.OperatorsAndFunctions.html
object ConditionExpression {
  private[dynamodb] sealed trait Operand[-From, +To] { self =>
    def between(minValue: AttributeValue, maxValue: AttributeValue): ConditionExpression[From] =
      Between(self.asInstanceOf[Operand[From, To]], minValue, maxValue)
    def in(values: Set[AttributeValue]): ConditionExpression[From]                             = In(self.asInstanceOf[Operand[From, To]], values)

    def ===[From2 <: From, To2 >: To](that: Operand[From2, To2]): ConditionExpression[From2] =
      Equals(self.asInstanceOf[Operand[From2, To2]], that)
    def <>[From2 <: From, To2 >: To](that: Operand[From2, To2]): ConditionExpression[From2]  =
      NotEqual(self.asInstanceOf[Operand[From, To2]], that)
    def <[From2 <: From, To2 >: To](that: Operand[From2, To2]): ConditionExpression[From2]   =
      LessThan(self.asInstanceOf[Operand[From2, To2]], that)
    def <=[From2 <: From, To2 >: To](that: Operand[From2, To2]): ConditionExpression[From2]  =
      LessThanOrEqual(self.asInstanceOf[Operand[From2, To2]], that)
    def >[From2 <: From, To2 >: To](that: Operand[From2, To2]): ConditionExpression[From2]   =
      GreaterThanOrEqual(self.asInstanceOf[Operand[From2, To2]], that)
    def >=[From2 <: From, To2 >: To](that: Operand[From2, To2]): ConditionExpression[From2]  =
      GreaterThanOrEqual(self.asInstanceOf[Operand[From2, To2]], that)

    def ===[From2 <: From, A](that: A)(implicit t: ToAttributeValue[A]): ConditionExpression[From2] =
      Equals(self.asInstanceOf[Operand[From, To]], Operand.ValueOperand(t.toAttributeValue(that)))
    def <>[From2 <: From, A](that: A)(implicit t: ToAttributeValue[A]): ConditionExpression[From2]  =
      NotEqual(self.asInstanceOf[Operand[From, To]], Operand.ValueOperand(t.toAttributeValue(that)))
    def <[From2 <: From, A](that: A)(implicit t: ToAttributeValue[A]): ConditionExpression[From2]   =
      LessThan(self.asInstanceOf[Operand[From, To]], Operand.ValueOperand(t.toAttributeValue(that)))
    def <=[From2 <: From, A](that: A)(implicit t: ToAttributeValue[A]): ConditionExpression[From2]  =
      LessThanOrEqual(self.asInstanceOf[Operand[From, To]], Operand.ValueOperand(t.toAttributeValue(that)))

    def >[From2 <: From, A](that: A)(implicit t: ToAttributeValue[A]): ConditionExpression[From2]  =
      GreaterThan(self.asInstanceOf[Operand[From, To]], Operand.ValueOperand(t.toAttributeValue(that)))
    def >=[From2 <: From, A](that: A)(implicit t: ToAttributeValue[A]): ConditionExpression[From2] =
      GreaterThanOrEqual(self.asInstanceOf[Operand[From, To]], Operand.ValueOperand(t.toAttributeValue(that)))

    def render: AliasMapRender[String] =
      self match {
        case op: Operand.ProjectionExpressionOperand[_] => AliasMapRender.succeed(op.pe.toString)
        case op: Operand.ValueOperand[_]                => AliasMapRender.getOrInsert(op.value).map(identity)
        case op: Operand.Size[_, _]                     => AliasMapRender.succeed(s"size(${op.path})")
      }
  }

  object Operand {

    private[dynamodb] final case class ProjectionExpressionOperand[From](pe: ProjectionExpression[From, _])
        extends Operand[From, Any] // TODO: Avi is Any OK?
    private[dynamodb] final case class ValueOperand[From](value: AttributeValue)
        extends Operand[From, Any] // TODO: Avi is Any OK?
    // needs to extend Operand[From, Long]
    private[dynamodb] final case class Size[-From, To](path: ProjectionExpression[From, To], ev: Sizable[To])
        extends Operand[From, Long]

  }

  private[dynamodb] final case class Between[From](
    left: Operand[From, Any],
    minValue: AttributeValue,
    maxValue: AttributeValue
  ) extends ConditionExpression[From]
  private[dynamodb] final case class In[From](left: Operand[From, Any], values: Set[AttributeValue])
      extends ConditionExpression[From]

  // functions
  private[dynamodb] final case class AttributeExists[From](path: ProjectionExpression[From, _])
      extends ConditionExpression[From]
  private[dynamodb] final case class AttributeNotExists[From](path: ProjectionExpression[From, _])
      extends ConditionExpression[From]
  private[dynamodb] final case class AttributeType[From](
    path: ProjectionExpression[From, _],
    attributeType: AttributeValueType
  ) extends ConditionExpression[From]
  private[dynamodb] final case class Contains[From](path: ProjectionExpression[From, _], value: AttributeValue)
      extends ConditionExpression[From]
  private[dynamodb] final case class BeginsWith[From](path: ProjectionExpression[From, _], value: AttributeValue)
      extends ConditionExpression[From]

  // logical operators
  private[dynamodb] final case class And[From](left: ConditionExpression[From], right: ConditionExpression[From])
      extends ConditionExpression[From]
  private[dynamodb] final case class Or[From](left: ConditionExpression[From], right: ConditionExpression[From])
      extends ConditionExpression[From]
  private[dynamodb] final case class Not[From](exprn: ConditionExpression[From]) extends ConditionExpression[From]

  // comparators
  private[dynamodb] final case class Equals[From](left: Operand[From, Any], right: Operand[From, Any])
      extends ConditionExpression[Any]
  private[dynamodb] final case class NotEqual[From](left: Operand[From, Any], right: Operand[From, Any])
      extends ConditionExpression[Any]
  private[dynamodb] final case class LessThan[From](left: Operand[From, Any], right: Operand[From, Any])
      extends ConditionExpression[Any]
  private[dynamodb] final case class GreaterThan[From](left: Operand[From, Any], right: Operand[From, Any])
      extends ConditionExpression[Any]
  private[dynamodb] final case class LessThanOrEqual[From](left: Operand[From, Any], right: Operand[From, Any])
      extends ConditionExpression[Any]
  private[dynamodb] final case class GreaterThanOrEqual[From](left: Operand[From, Any], right: Operand[From, Any])
      extends ConditionExpression[Any]
}
