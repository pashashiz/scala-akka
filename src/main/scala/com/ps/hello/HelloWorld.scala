package com.ps.hello

import akka.actor.{Actor, Props}

class HelloWorld extends Actor {

  override def preStart(): Unit = {
    // create the greater actor
    val greeter = context.actorOf(Props[Greeter], "greeter")
    // tell it to perform the greeting
    greeter ! Greeter.Greet
  }

  override def receive: Receive = {
    // when the greeter is done, stop this actor and with it the application
    case Greeter.Done => context.stop(self)
  }
}
