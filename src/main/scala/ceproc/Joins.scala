package ceproc
package joins

trait Index[A, B] {
  def apply(a: A): B
}

object Index {
  def apply[A, B](f: A => B): Index[A, B] = 
    new Index[A, B] {
      def apply(a: A) = f(a)
    }
  private val ixAny = 
    new Index[Any, Any] {
      def apply(a: Any) = a
    }
  def whole[A]: Index[A, A] = ixAny.asInstanceOf[Index[A, A]]
}

trait JoinSpec[L, R, Q] { parent =>
  type Key
  def classifyL: Index[L, Key]
  def classifyR: Index[R, Key]
  def filter(l: L, r: R): Boolean
  final def satisfy(l: L, r: R): Boolean = 
    classifyL(l) == classifyR(r) && filter(l, r)
  def combine(l: L, r: R): Q
  def converse: JoinSpec[R, L, Q] = 
    new JoinSpec[R, L, Q] {
      type Key = parent.Key
      def classifyL = parent.classifyR
      def classifyR = parent.classifyL
      def filter(r: R, l: L) = parent.filter(l, r)
      def combine(r: R, l: L) = parent.combine(l, r)
    }
}

trait ProJoin[L, R] extends JoinSpec[L, R, (L, R)] {
  final def combine(l: L, r: R) = (l, r)
}

trait EquiJoin[L, R, K] extends ProJoin[L, R] {
  type Key = K
  final def filter(l: L, r: R) = true
}

class Intersection[P] extends JoinSpec[P, P, P] {
  type Key = P
  def classifyL = Index.whole
  def classifyR = Index.whole
  def filter(l: P, r: P) = true
  def combine(l: P, r: P) = l 
}

object JoinSpec {
  def equiJoin[L, R, K](fl: Index[L, K], fr: Index[R, K]): EquiJoin[L, R, K] =
    new EquiJoin[L, R, K] {
      def classifyL = fl
      def classifyR = fr
    }
  def intersection[P]: Intersection[P] 
    = new Intersection[P]
}
