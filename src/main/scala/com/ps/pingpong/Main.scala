package com.ps.pingpong

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}

object Main {

  def main(args: Array[String]): Unit = {
    var system = ActorSystem("PingPongSystem")
    val pong = system.actorOf(Props[Pong], name = "pong")
    val ping = system.actorOf(Props(new Ping(pong)), name = "ping")
    system.actorOf(Props(classOf[Terminator], ping), "terminator")
    ping ! StartMessage
  }

  class Terminator(ref: ActorRef) extends Actor with ActorLogging {
    context watch ref
    def receive: Receive = {
      case Terminated(_) =>
        log.info("{} has terminated, shutting down system", ref.path)
        context.system.terminate()
    }
  }

  case object StartMessage
  case object StopMessage
  case object PingMessage

  class Ping(pong: ActorRef) extends Actor {
    var count = 0
    def incrementAndPrint: Unit = {
      count += 1
      println("ping")
    }
    override def receive: Receive = {
      case StartMessage =>
        incrementAndPrint
        pong ! PingMessage
      case PingMessage =>
        incrementAndPrint
        if (count > 9) {
          sender ! StopMessage
          println("ping stopped")
          context.stop(self)
        } else {
          sender ! PingMessage
        }
    }
  }

  class Pong extends Actor{
    override def receive: Receive = {
      case PingMessage =>
        println("pong")
        sender ! PingMessage
      case StopMessage =>
        println("pong stopped")
        context.stop(self)
    }
  }
}
