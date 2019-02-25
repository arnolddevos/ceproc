package ceproc
package runtime
import joins._
import data._

import scala.language.higherKinds

final class Queue(t0: Time, td: Duration) {
  val tenured, output, latest, pending = new Pool(t0)
  var timeCleaned: Time = t0

  def produce( k: Pool => Unit): Unit = {
    k(output)
    output.clear()
  }

  def consume( k: Pool => Unit): Unit = {
    k(pending)
  }

  def clean(): Unit = {
    val now = tenured.getTime
    if(timeCleaned == t0)
      timeCleaned = now
    else if(now - timeCleaned > td) {
      tenured.clean(timeCleaned)
      timeCleaned = now
    }
  }

  def iterate(k: Queue => Unit): Unit = {
    latest.union(pending)
    pending.clear()
    latest.diff(tenured)
    if(! latest.isEmpty) {
      output.union(latest)
      tenured.union(latest)
      k(this)
      iterate(k)
      latest.clear()
    }
  }
} 

final class Pool(t0: Time) {

  import scala.collection.mutable.{Set, Map}
  type TimeLens[A] = A => Time 

  final class KeyMap[A] {
    type Contents[K] = Map[Index[A, K], Map[K, Set[A]]]
    private val untyped: Contents[Any] = Map.empty
    def contents[K] = untyped.asInstanceOf[Contents[K]]
  }

  final class VarMap[F[_]](z: => F[Any]) {
    type Contents[A] = Map[Var[A], F[A]]
    private val untyped: Contents[Any] = Map.empty.withDefault(v => z)
    def contents[A] = untyped.asInstanceOf[Contents[A]]
  }

  private val store   = new VarMap[Set](Set.empty)
  private val indices = new VarMap[KeyMap](new KeyMap)
  private val times   = new VarMap[TimeLens](a => t0)

  private var greatestSeen: Time = t0

  private def updateOneTime(t: Time): Unit = {
    if(t > greatestSeen)
      greatestSeen = t
  } 

  private def updateTime[A](sa: Iterable[A], f: A => Time): Unit = {
    for(a <- sa)
      updateOneTime(f(a))
  }

  def clear(): Unit = {
    store.contents.clear()
    indices.contents.clear()
  }

  def getTime(): Time = greatestSeen

  def buildIndex[A, K](v: Var[A], f: Index[A, K]): Unit = {
    val g: Map[K, Set[A]] = Map.empty.withDefault(k => Set.empty)
    for(a <- store.contents(v)) 
      g(f(a)) += a
    indices.contents(v).contents(f) = g
  } 

  def get[A](v: Var[A]): Iterable[A] = store.contents(v)
  
  def getIndex[A, K](v: Var[A], f: Index[A, K]): K => Iterable[A] = {
    if(! (indices.contents(v).contents contains f)) 
      buildIndex(v, f) 
    indices.contents(v).contents(f)
  }
  
  def put[A](v: Var[A], sa: Iterable[A]): Unit = {
    def _updateKeys[K] =
      for {
        (f, g) <- indices.contents(v).contents[K]
        a <- sa
      } {
        g(f(a)) += a 
      }

    store.contents(v) ++= sa
    if( indices.contents contains v) 
      _updateKeys[Any]
    if(times.contents contains v) 
      updateTime(sa, times.contents(v))
  }

  def union(p: Pool): Unit = {
    def _union[A] = 
      for((v, rs) <- p.store.contents[A])
        put(v, rs)
    _union[Any]
    updateOneTime(p.greatestSeen)
  }

  def diff(p: Pool): Unit = {
    def _diff[A] = 
      for((v, rs) <- p.store.contents[A] if store.contents contains v) {
        val rs0 = store.contents(v) 
        rs0 --= rs
        if( rs0.isEmpty )
          store.contents -= v
      }
    _diff[Any]
    indices.contents.clear()
    updateOneTime(p.greatestSeen)
  }

  def isEmpty: Boolean =  store.contents[Any].forall { case (v, rs) => rs.isEmpty }

  def putTime[A](v: Var[A])(f: A => Time): Unit = {
    times.contents(v) = f
    if(store.contents contains v)
      updateTime(store.contents(v), f) 
  }

  def clean(t: Time): Unit = {
    def _clean[A] =
      for((v, f) <- times.contents[A]) {
        val sa = store.contents(v)
        val na0 = sa.size
        if( na0 > 0) {
          sa.filterInPlace(a => f(a) > t)
          val na1 = sa.size
          if(na0 != na1)
            indices.contents(v).contents.clear
        }
      }
    _clean[Any]
  }
}
