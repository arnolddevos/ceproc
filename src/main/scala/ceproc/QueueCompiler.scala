package ceproc
package runtime
import joins._
import data._
import relational._

import scala.language.higherKinds

import Transform.transformSystem

object QueueCompiler extends RelationalAlgebra {

  type Qop[A] = Queue => A
  type Rel[A] = Qop[Iterable[A]]
  type Rep[A] = (Exp[A], Rel[A])

  def second[A, B](a: Qop[A], b: Qop[B]): Qop[B] = q => { a(q); b(q) }

  def indexFor[A, K](r: Rep[A], i: Index[A, K]): Qop[K => Iterable[A]] = 
    r match {
      case (Ref(v), a)      =>  _.tenured.getIndex(v, i)
      case (Recur(v, _), a) =>  second( a, _.tenured.getIndex(v, i))
      case (_, a)           =>  
                                val v = Var[A]
                                second(recur(v, a), _.tenured.getIndex(v, i))
    }

  def let[A, B](v: Var[B], b: Rel[B], a: Rel[A]): Rel[A] = 
    second(q => q.pending.put(v, b(q)), a)

  def ref[A](v: Var[A]): Rel[A] = q => q.latest.get(v)

  def recur[A](v: Var[A], a: Rel[A]): Rel[A] =
    q => { 
      val sa = a(q)
      q.pending.put(v, sa)
      sa
    }

  def union[A](l: Rel[A], r: Rel[A]): Rel[A] = q => l(q) ++ r(q)

  def joinRight[L, R, Q](j: JoinSpec[L, R, Q], l: Rep[L], r: Rep[R]): Rel[Q] = {
    val index = indexFor(r, j.classifyR)
    val (_, l1) = l
    q => {
      val sl = l1(q)
      val fr = index(q)
      for { 
        x <- sl
        y <- fr(j.classifyL(x)) if j.satisfy(x, y)
      }
      yield j.combine(x, y)
    }
  }

  def join[L, R, Q](j: JoinSpec[L, R, Q], l: Rep[L], r: Rep[R]): Rel[Q] = 
    union(joinRight(j, l, r), joinRight(j.converse, r, l))

  def compileKernel = 
    new FunctionK[Exp, Rep]{

      def apply[A](e: Exp[A]): Rep[A] = (e, qop(e))

      def qop[A](e: Exp[A]): Rel[A] =
        e match {
          case Select(f, (_, a))      =>  a.andThen(_.filter(f))
          case Join(j, l, r)          =>  join(j, l, r)
          case Project(f, (_, a))     =>  a.andThen(_.map(f))
          case Union((_, l), (_, r))  =>  union(l, r)
          case Intersect(l, r)        =>  join(JoinSpec.intersection[A], l, r)
          case Let(v, (_, b), (_, a)) =>  let(v, b, a)
          case Ref(v)                 =>  ref(v)
          case Recur(v, (_, a))       =>  recur(v, a)
          case Fail(e)                =>  q => Iterable.empty
        }
    }

  import RelationalAlgebra.tree
  import tree.System

  def compile[A](x: tree.Exp[A]): Rel[A] = {
    fold(compileKernel)(x)._2
  }

  def compileSystem(s: System): Qop[Unit] = {

    def compilingFold =
      new Table.Fold[Qop[Unit], tree.Exp] {
        def init = q => ()
        def apply[A](z: Qop[Unit], v: Var[A], e: tree.Exp[A]): Qop[Unit] =
          second(compile(e), z)
      }

    transformSystem(s).globals.fold(compilingFold)
  }
}
