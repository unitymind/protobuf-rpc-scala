//       ___       ___       ___       ___       ___
//      /\  \     /\__\     /\__\     /\  \     /\__\
//     /::\  \   /:/ _/_   /:| _|_   /::\  \   /:/  /
//    /::\:\__\ /::-"\__\ /::|/\__\ /::\:\__\ /:/__/
//    \;:::/  / \;:;-",-" \/|::/  / \;:::/  / \:\  \
//     |:\/__/   |:|  |     |:/  /   |:\/__/   \:\__\
//      \|__|     \|__|     \/__/     \|__|     \/__/

package ru.rknrl.rpc

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp.{Bind, Bound, CommandFailed, Connected}
import akka.io.{IO, Tcp}

object Server {
  def props(host: String, port: Int, acceptWithActor: ActorRef ⇒ Props, serializer: Serializer) =
    Props(classOf[Server], host, port, acceptWithActor, serializer)
}

class Server(host: String, port: Int, acceptWithActor: ActorRef ⇒ Props, serializer: Serializer) extends Actor with ActorLogging {
  val address = new InetSocketAddress(host, port)

  import context.system

  IO(Tcp) ! Bind(self, address)

  def receive = {
    case Bound(localAddress) ⇒
      log.info("bound " + localAddress)

    case CommandFailed(_: Bind) ⇒
      log.info("command failed " + address)
      context stop self

    case Connected(remote, local) ⇒
      val name = remote.getAddress.getHostAddress + ":" + remote.getPort
      log.debug("connected " + name)

      context.actorOf(
        ClientSession.props(sender, acceptWithActor, serializer),
        "client-session-" + name.replace('.', '-')
      )
  }
}

