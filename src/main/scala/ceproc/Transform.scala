package ceproc
package relational

object Transform extends RelationalAlgebra {
  
  import RelationalAlgebra.tree
  import tree.System
  import Failure._

  case class Rep[A](syms: Table[Var], exp: tree.Exp[A])

  def rename[A](t: Table[Var], v: Var[A]) = {
    def v1 = t.get(v).fold(v)(_ => Var[A])
    def t1 = t.updated(v, v1)
    (t1, v1)
  }

  def let[A, B](t: Table[Var], v: Var[A], a: tree.Exp[A], b: tree.Exp[B]): Exp[B] = {
    val (t1, v1) = rename(t, v)
    Let(v1, Rep(t, a), Rep(t1, b))
  }

  def ref[A](t: Table[Var], v: Var[A]): Exp[A] = {
    def fail: Exp[A] = Fail(fail_unbound_variable)
    t.get(v).fold(fail)(Ref(_))
  }

  def recur[A](t: Table[Var], v: Var[A], a: tree.Exp[A]): Exp[A] = {
    val (t1, v1) = rename(t, v)
    Recur(v1, Rep(t1, a))
  }

  def transformKernel =
    new FunctionK[Rep, Exp] {
      def apply[A](ta: Rep[A]): Exp[A] = {

        val Rep(t, x) = ta
        def annot[X](x: tree.Exp[X]) = Rep(t, x)
        
        x match {
          case tree.Select(f, a)    => Select(f, annot(a))
          case tree.Project(f, a)   => Project(f, annot(a))
          case tree.Join(j, l, r)   => Join(j, annot(l), annot(r))
          case tree.Union(l, r)     => Union(annot(l), annot(r))
          case tree.Intersect(l, r) => Intersect(annot(l), annot(r))
          case tree.Let(v, a, b)    => let(t, v, a, b)
          case tree.Ref(v)          => ref(t, v)
          case tree.Recur(v, a)     => recur(t, v, a)
          case tree.Fail(e)         => Fail(e)
        }
      }
    }

  def transform[A](x: tree.Exp[A], t: Table[Var]): tree.Exp[A] =
    unfold(transformKernel)(Rep(t, x))

  def transformSystem(s: System): System = {
    def collectGlobals =
      new Table.Fold[Table[Var], tree.Exp] {
        def init = Table[Var]
        def apply[A](z: Table[Var], v: Var[A], e: tree.Exp[A]): Table[Var] = z.updated(v, v)
      }

    val globals = s.globals.fold(collectGlobals)

    def transformGlobals =
      new Table.Fold[Table[tree.Exp], tree.Exp] {
        def init = Table[tree.Exp]
        def apply[A](z: Table[tree.Exp], v: Var[A], e: tree.Exp[A]): Table[tree.Exp] =
          z.updated(v, transform(e, globals))
      }

    System(s.globals.fold(transformGlobals))
  }
}
