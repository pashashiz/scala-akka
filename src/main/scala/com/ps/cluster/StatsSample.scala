package com.ps.cluster

import scala.concurrent.duration._
import java.util.concurrent.ThreadLocalRandom

import com.typesafe.config.ConfigFactory
import akka.actor.{Actor, ActorRef, ActorSystem, Address, Props, ReceiveTimeout, RelativeActorPath, RootActorPath}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus
import akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope
import akka.routing.FromConfig

object StatsSample {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      startup(Seq("2551", "2552", "0"))
      StatsSampleClient.main(Array.empty)
    } else {
      startup(args)
    }
  }

  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      // Override the configuration of the port when specified as program argument
      val config = ConfigFactory.parseString(
        s"""
        akka {
          actor {
            provider = "akka.cluster.ClusterActorRefProvider"
            deployment {
              /statsService/workerRouter {
                router = consistent-hashing-group
                routees.paths = ["/user/statsWorker"]
                cluster {
                  enabled = on
                  allow-local-routees = on
                  use-role = compute
                }
              }
            }
          }
          remote {
            log-remote-lifecycle-events = off
            netty.tcp {
              hostname = "127.0.0.1"
              port = $port
            }
          }
          cluster {
            seed-nodes = [
             "akka.tcp://ClusterSystem@127.0.0.1:2551",
             "akka.tcp://ClusterSystem@127.0.0.1:2552"]
            roles = [compute]
           # auto downing is NOT safe for production deployments.
           # you may want to use it during development, read more about it in the docs.
           auto-down-unreachable-after = 10s
          }
        }

        # Disable legacy metrics in akka-cluster.
        akka.cluster.metrics.enabled=off

        # Enable metrics extension in akka-cluster-metrics.
        akka.extensions=["akka.cluster.metrics.ClusterMetricsExtension"]
        """)

      val system = ActorSystem("ClusterSystem", config)

      system.actorOf(Props[StatsWorker], name = "statsWorker")
      system.actorOf(Props[StatsService], name = "statsService")
    }
  }
}

final case class StatsJob(text: String)

final case class StatsResult(meanWordLength: Double)

final case class JobFailed(reason: String)

class StatsService extends Actor {
  // This router is used both with lookup and deploy of routees. If you
  // have a router with only lookup of routees you can use Props.empty
  // instead of Props[StatsWorker.class].
  val workerRouter = context.actorOf(FromConfig.props(Props[StatsWorker]), name = "workerRouter")

  def receive: Receive = {
    case StatsJob(text) if text != "" =>
      println("Recieved words: " + text)
      val words = text.split(" ")
      val replyTo = sender() // important to not close over sender()
      // create actor that collects replies from workers
      val aggregator = context.actorOf(Props(new StatsAggregator(words.size, replyTo)))
      words foreach { word =>
        println("call worker")
        workerRouter.tell(
          ConsistentHashableEnvelope(word, word), aggregator)
      }
  }
}

class StatsAggregator(expectedResults: Int, replyTo: ActorRef) extends Actor {
  var results = IndexedSeq.empty[Int]
  context.setReceiveTimeout(30.seconds)

  def receive: Receive = {
    case wordCount: Int =>
      println("Recieved word count: " + wordCount)
      results = results :+ wordCount
      if (results.size == expectedResults) {
        val meanWordLength = results.sum.toDouble / results.size
        replyTo ! StatsResult(meanWordLength)
        context.stop(self)
      }
    case ReceiveTimeout =>
      replyTo ! JobFailed("Service unavailable, try again later")
      context.stop(self)
  }
}

class StatsWorker extends Actor {
  var cache = Map.empty[String, Int]

  def receive: Receive = {
    case word: String =>
      println("Received a word: " + word)
      val length = cache.get(word) match {
        case Some(x) => x
        case None =>
          val x = word.length
          cache += (word -> x)
          x
      }

      sender() ! length
    case _ => println("???")
  }
}

object StatsSampleClient {
  def main(args: Array[String]): Unit = {
    // note that client is not a compute node, role not defined
    val config = ConfigFactory.parseString(
      s"""
        akka {
          actor {
            provider = "akka.cluster.ClusterActorRefProvider"
          }
          remote {
            log-remote-lifecycle-events = off
            netty.tcp {
              hostname = "127.0.0.1"
              port = 0
            }
          }
          cluster {
           seed-nodes = [
             "akka.tcp://ClusterSystem@127.0.0.1:2551",
             "akka.tcp://ClusterSystem@127.0.0.1:2552"]

           # auto downing is NOT safe for production deployments.
           # you may want to use it during development, read more about it in the docs.
           auto-down-unreachable-after = 10s
          }
        }

        # Disable legacy metrics in akka-cluster.
        akka.cluster.metrics.enabled=off

        # Enable metrics extension in akka-cluster-metrics.
        akka.extensions=["akka.cluster.metrics.ClusterMetricsExtension"]
        """)
    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(Props(classOf[StatsSampleClient], "/user/statsService"), "client")
  }
}

class StatsSampleClient(servicePath: String) extends Actor {
  val cluster = Cluster(context.system)
  val servicePathElements = servicePath match {
    case RelativeActorPath(elements) => elements
    case _ => throw new IllegalArgumentException(
      "servicePath [%s] is not a valid relative actor path" format servicePath)
  }

  import context.dispatcher

  val tickTask = context.system.scheduler.schedule(2.seconds, 2.seconds, self, "tick")

  var nodes = Set.empty[Address]

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
    tickTask.cancel()
  }

  def receive: Receive = {
    case "tick" if nodes.nonEmpty =>
      println("tick")
      // just pick any one
      val address = nodes.toIndexedSeq(ThreadLocalRandom.current.nextInt(nodes.size))
      val service = context.actorSelection(RootActorPath(address) / servicePathElements)
      service ! StatsJob("this is the text that will be analyzed")
    case result: StatsResult =>
      println(result)
    case failed: JobFailed =>
      println(failed)
    case state: CurrentClusterState =>
      nodes = state.members.collect {
        case m if m.status == MemberStatus.Up => m.address
      }
    case MemberUp(m) => nodes += m.address
    case other: MemberEvent => nodes -= other.member.address
    case UnreachableMember(m) => nodes -= m.address
    case ReachableMember(m) => nodes += m.address
  }

}