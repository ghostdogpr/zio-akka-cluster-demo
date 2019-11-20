package zio.akka.cluster.demo

import akka.actor.ActorSystem
import zio.akka.cluster.pubsub.{ PubSub, Publisher }
import zio.akka.cluster.sharding.{ Entity, Sharding }
import zio.console.Console
import zio._

object MainApp extends App {

  sealed trait ChatMessage
  case class Message(name: String, msg: String) extends ChatMessage
  case class Join(name: String)                 extends ChatMessage
  case class Leave(name: String)                extends ChatMessage

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    Managed
      .make(Task(ActorSystem("Chat")))(sys => Task(sys.terminate()).ignore)
      .use(
        actorSystem =>
          for {
            sharding <- startChatServer.provide(actorSystem)
            _        <- console.putStrLn("Hi! What's your name? (Type [exit] to stop)")
            name     <- console.getStrLn
            _        <- ZIO.when(name.toLowerCase == "exit" || name.trim.isEmpty)(ZIO.interrupt)
            _        <- console.putStrLn("Hi! Which chatroom do you want to join? (Type [exit] to stop)")
            room     <- console.getStrLn
            _        <- ZIO.when(room.toLowerCase == "exit" || room.trim.isEmpty)(ZIO.interrupt)
            _        <- joinChat(name, room, actorSystem, sharding)
          } yield 0
      )
      .catchAll(e => console.putStrLn(e.toString).as(1))

  def chatroomBehavior(pubSub: Publisher[String])(msg: ChatMessage): ZIO[Entity[List[String]], Nothing, Unit] =
    for {
      entity <- ZIO.environment[Entity[List[String]]]
      toSend <- msg match {
                 case Message(name, m) => IO.succeed(s"$name: $m")
                 case Join(name) =>
                   entity.state
                     .update(state => Some(name :: state.getOrElse(Nil)))
                     .map(state => s"$name joined the room. There are now ${state.getOrElse(Nil).size} participant(s).")
                 case Leave(name) =>
                   entity.state
                     .update(state => Some(state.getOrElse(Nil).filterNot(_ == name)))
                     .map(state => s"$name left the room. There are now ${state.getOrElse(Nil).size} participant(s).")
               }
      _ <- pubSub.publish(entity.id, toSend).ignore
    } yield ()

  val startChatServer: ZIO[ActorSystem, Throwable, Sharding[ChatMessage]] =
    for {
      pubSub   <- PubSub.createPublisher[String]
      sharding <- Sharding.start[ChatMessage, List[String]]("Chat", chatroomBehavior(pubSub))
    } yield sharding

  def joinChat(
    name: String,
    room: String,
    actorSystem: ActorSystem,
    sharding: Sharding[ChatMessage]
  ): ZIO[Console, Throwable, Unit] =
    for {
      pubSub   <- PubSub.createSubscriber[String].provide(actorSystem)
      messages <- pubSub.listen(room)
      _        <- messages.take.flatMap(console.putStrLn).forever.fork
      _        <- sharding.send(room, Join(name))
      _        <- chat(name, room, sharding)
    } yield ()

  def chat(name: String, room: String, sharding: Sharding[ChatMessage]): ZIO[Console, Throwable, Unit] =
    (for {
      msg <- console.getStrLn
      _   <- ZIO.when(msg.toLowerCase == "exit")(sharding.send(room, Leave(name)) *> ZIO.interrupt)
      _   <- ZIO.when(msg.trim.nonEmpty)(sharding.send(room, Message(name, msg)))
    } yield ()).forever

}
