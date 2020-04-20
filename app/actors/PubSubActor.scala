package actors

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, Unsubscribe, UnsubscribeAck}

object PubSubActor {

  def props() = Props(new PubSubActor)

}

/**
  * When WebSocketActor subscribes to PubSub, it should automatically get unsubscribed
  * when it stops. But the Terminated message never reaches DistributedPubSubMediator.
  * I can't reproduce this in a new project but I also can't fix it here.
  * So we can send our own Unsubscribe message instead, BUT it tries to reply with an
  * UnsubscribeAck message, which is harmless but makes an annoying "dead letter" as the
  * sender has already stopped.
  *
  * So this actor exists solely to pass on the pubsub messages, and quietly ignore
  * the UnsubscribeAck.
  */
class PubSubActor extends Actor with ActorLogging {

  private lazy val mediator = DistributedPubSub(context.system).mediator

  override def receive: Receive = {
    // forward subscribes, ack will go to the original sender.
    case msg: Subscribe => mediator forward msg
    // send this message so PubSubActor is the sender and gets the ack.
    case msg: Unsubscribe => mediator ! msg
    // ignore unsubscribe acks.
    case _: UnsubscribeAck =>
    case msg => log.info(s"Unrecognised message: $msg")
  }

}
