package com.ps.mergesort

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.ps.mergesort.Messages.MergeSort

import scala.concurrent.duration._
import scala.language.postfixOps

object Main {

  def main(args: Array[String]): Unit = {
    val actorSystem = ActorSystem("sort-system")
    val sorterActor = actorSystem.actorOf(SorterActor.build, name = "sorter")

    // generate random numbers
    val random = new scala.util.Random
    val list = (1 to 10000 map(_ => random.nextInt(10000))).toList

    // test normal sorting
    var startTime = System.currentTimeMillis()
    SortAlgorithm.mergeSort(list)
    var endTime = System.currentTimeMillis() - startTime
    println("Normal sorting: " + endTime)

    // setup timeout and dispatcher
    implicit val timeout = Timeout.apply(60 seconds)
    import actorSystem.dispatcher

    // test sorting using actors
    startTime = System.currentTimeMillis()
    sorterActor.ask(MergeSort(list)).map(result => {
      endTime = System.currentTimeMillis() - startTime
      println("Actor sorting: " + endTime)
      actorSystem.terminate()
    })
  }
}