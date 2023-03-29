package zio.dynamodb

import com.github.ghik.silencer.silent
import scala.annotation.tailrec

object EitherUtil {
  def forEach[A, B, E](list: Iterable[A])(f: A => Either[E, B]): Either[E, Iterable[B]] = {
    @tailrec
    @silent("Iterable")
    def loop[A2, B2, E2](xs: Iterable[A2], acc: List[B2])(f: A2 => Either[E2, B2]): Either[E2, Iterable[B2]] =
      xs match {
        case head :: tail =>
          f(head) match {
            case Left(e)  => Left(e)
            case Right(a) => loop(tail, a :: acc)(f)
          }
        case Nil          => Right(acc.reverse)
      }

    loop(list.toList, List.empty)(f)
  }

  def collectAll[A, E](list: Iterable[Either[E, A]]): Either[E, Iterable[A]] = forEach(list)(identity)
}
