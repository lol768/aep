package services

import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.google.inject.{ImplementedBy, Inject}

// Borrowed from B-A-D to abstract over Akka
@ImplementedBy(classOf[AkkaPubSub])
trait PubSub {
  def publish(topic: String, message: Any): Unit
}

class AkkaPubSub @Inject() (akka: ActorSystem) extends PubSub {
  private val pubsub = DistributedPubSub(akka)
  private val mediator = pubsub.mediator

  override def publish(topic: String, message: Any) = mediator ! Publish(topic, message)
}
