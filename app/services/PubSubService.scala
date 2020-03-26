package services

import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import com.google.inject.{ImplementedBy, Inject}

// Borrowed from B-A-D to abstract over Akka
@ImplementedBy(classOf[AkkaPubSubService])
trait PubSubService {
  def publish(topic: String, message: Any): Unit

  def subscribe(topic: String, group: Option[String]): Unit
}

class AkkaPubSubService @Inject()(akka: ActorSystem) extends PubSubService {
  private val pubsub = DistributedPubSub(akka)
  private val mediator = pubsub.mediator

  override def publish(topic: String, message: Any): Unit = mediator ! Publish(topic, message)

  override def subscribe(topic: String, group: Option[String]): Unit = mediator ! Subscribe(topic, group, mediator)
}
