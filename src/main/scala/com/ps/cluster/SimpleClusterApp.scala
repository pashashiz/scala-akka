package com.ps.cluster

import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object SimpleClusterApp {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("ClusterSystem", ConfigFactory.parseString("""
    akka {
      actor {
        provider = cluster
      }
      remote {
        log-remote-lifecycle-events = off
        netty.tcp {
          hostname = "127.0.0.1"
          port = 2552
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
    """))
    system.actorOf(Props[SimpleClusterListener], name = "clusterListener")
  }
}

class SimpleClusterListener extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, re-subscribe when restart
  override def preStart(): Unit = {
    //#subscribe
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
    //#subscribe
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)
    case UnreachableMember(member) =>
      log.info("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info(
        "Member is Removed: {} after {}",
        member.address, previousStatus)
    case _: MemberEvent => // ignore
  }
}
