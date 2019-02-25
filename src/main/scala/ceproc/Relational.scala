package ceproc
package relational
import joins._
import data._

import scala.language.higherKinds

trait RelationalAlgebra { algebra =>
  type Rep[_]
  sealed trait Exp[P]
  case class Select[P](f: P => Boolean, p: Rep[P]) extends Exp[P]
  case class Join[L, R, Q](j: JoinSpec[L, R, Q], l: Rep[L], r: Rep[R]) extends Exp[Q]
  case class Project[P, Q](f: P => Q, p: Rep[P]) extends Exp[Q]
  case class Union[P](l: Rep[P], r: Rep[P]) extends Exp[P] 
  case class Intersect[P](l: Rep[P], r: Rep[P]) extends Exp[P]
  case class Let[P, Q](v: Var[P], p: Rep[P], q: Rep[Q]) extends Exp[Q]
  case class Ref[P](v: Var[P]) extends Exp[P]
  case class Recur[P](v: Var[P], p: Rep[P]) extends Exp[P]
  case class Fail[P](e: Failure) extends Exp[P]

  import RelationalAlgebra.tree

  def fold(f: FunctionK[Exp, Rep]): FunctionK[tree.Exp, Rep] = 
    new FunctionK[tree.Exp, Rep] {
      private val g = tree.mapK(algebra)(this)
      def apply[A](a: tree.Exp[A]): Rep[A] = f(g(a))
    }

  def unfold(f: FunctionK[Rep, Exp]): FunctionK[Rep, tree.Exp] =
    new FunctionK[Rep, tree.Exp] {
      private val g = mapK(tree)(this)
      def apply[A](a: Rep[A]): tree.Exp[A] = g(f(a))
    }

  def mapK(s: RelationalAlgebra)(f: FunctionK[Rep, s.Rep]): FunctionK[Exp, s.Exp] =
    new FunctionK[Exp, s.Exp] {
      def apply[A](e: Exp[A]) =
        e match {
          case Select(g, a)    => s.Select(g, f(a))
          case Join(j, l, r)   => s.Join(j, f(l), f(r))
          case Project(g, a)   => s.Project(g, f(a))
          case Union(l, r)     => s.Union(f(l), f(r))
          case Intersect(l, r) => s.Intersect(f(l), f(r))
          case Let(v, p, a)    => s.Let(v, f(p), f(a))
          case Ref(v)          => s.Ref(v)
          case Recur(v, a)     => s.Recur(v, f(a))
          case Fail(x)         => s.Fail(x)
        }
    }
}

object RelationalAlgebra {
  object tree extends RelationalAlgebra {
    type Rep[A] = Exp[A]
    case class System(globals: Table[Exp])
  }
}
