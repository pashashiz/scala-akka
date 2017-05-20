package com.ps.mergesort

import akka.pattern.{ask, pipe}
import akka.actor.{Actor, Props}

import scala.concurrent.duration._
import akka.util.Timeout
import com.ps.mergesort.Messages._
import com.ps.mergesort.SorterActor.threshold

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class SorterActor extends Actor {
  override def receive: Receive = {
    case MergeSort(list) =>
      implicit val ec: ExecutionContext = context.system.dispatchers.lookup("akka.actor.sorting-dispatcher")
      implicit val timeout = Timeout(60 seconds)
      if (list.size < threshold) {
        sender ! SortAlgorithm.mergeSort(list)
      } else {
        // splitting work
        val left = context.actorOf(SorterActor.build
          .withDispatcher("akka.actor.sorting-dispatcher"), name = "left")
        val right = context.actorOf(SorterActor.build
          .withDispatcher("akka.actor.sorting-dispatcher"), name = "right")
        val (first, second) = list.splitAt(list.size / 2)
        // running
        val leftFuture = ask(left, MergeSort(first)).mapTo[List[Int]]
        val rightFuture = ask(right, MergeSort(second)).mapTo[List[Int]]
        // combining
        val resFuture: Future[(List[Int], List[Int])] = for {
          f1 <- leftFuture
          f2 <- rightFuture
        } yield (f1, f2)
        resFuture.map(result => {
          val (firstSorted, secondSorted) = result
          SortAlgorithm.merge(firstSorted, secondSorted)
        }).pipeTo(sender())
      }
  }
}

object SorterActor {
  val threshold = 1000
  def build = Props(new SorterActor)
}