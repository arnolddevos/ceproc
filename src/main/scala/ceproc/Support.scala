package ceproc
import scala.language.higherKinds


trait FunctionK[F[_], G[_]] {
  def apply[A](fa: F[A]): G[A]
}

trait Var[A]
object Var {
  def apply[A]: Var[A] = new Var[A] {}
}

class Failure

object Failure {
  val fail_unexpected_type = new Failure
  val fail_div_zero = new Failure
  val fail_unbound_variable = new Failure
}

object data {
  type Time = Long
  type Duration = Long
}

import Table.{Contents, Fold}

class Table[F[_]] private (untyped: Contents[Any, F]) {
  def contents[A] = untyped.asInstanceOf[Contents[A, F]]
  def get[A](v: Var[A]): Option[F[A]] = contents[A].get(v)
  def updated[A](v: Var[A], a: F[A]): Table[F] = 
    new Table[F](contents[A].updated(v, a).asInstanceOf[Contents[Any, F]])
  def fold[Z](f: Fold[Z, F]): Z = {
    def ff[A](z: Z, va: (Var[A], F[A])) = f(z, va._1, va._2)
    contents[Any].foldLeft(f.init)(ff)
  }
}

object Table {
  type Contents[A, F[_]] = Map[Var[A], F[A]]

  trait Fold[Z, F[_]] { 
    def init: Z
    def apply[A](z: Z, v: Var[A], a: F[A]): Z
  }

  def apply[F[_]] = new Table[F](Map.empty)
}
