//       ___       ___       ___       ___       ___
//      /\  \     /\__\     /\__\     /\  \     /\__\
//     /::\  \   /:/ _/_   /:| _|_   /::\  \   /:/  /
//    /::\:\__\ /::-"\__\ /::|/\__\ /::\:\__\ /:/__/
//    \;:::/  / \;:;-",-" \/|::/  / \;:::/  / \:\  \
//     |:\/__/   |:|  |     |:/  /   |:\/__/   \:\__\
//      \|__|     \|__|     \/__/     \|__|     \/__/

package ru.rknrl.rpc

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp
import akka.io.Tcp._
import akka.util.ByteString
import com.trueaccord.scalapb.GeneratedMessage
import ru.rknrl.rpc.ClientSession.CloseConnection

import scala.annotation.tailrec

object ClientSession {

  case object CloseConnection

  def props(tcp: ActorRef, acceptWithActor: ActorRef ⇒ Props, serializer: Serializer) =
    Props(classOf[ClientSession], tcp, acceptWithActor, serializer)
}

class ClientSession(var tcp: ActorRef, acceptWithActor: ActorRef ⇒ Props, serializer: Serializer) extends ClientSessionBase(acceptWithActor, serializer) {
  tcp ! Register(self)
}

abstract class ClientSessionBase(acceptWithActor: ActorRef ⇒ Props, serializer: Serializer) extends Actor with ActorLogging {

  var tcp: ActorRef

  case object Ack extends Event

  implicit val byteOrder = java.nio.ByteOrder.LITTLE_ENDIAN

  val headerSize = 4 + 4

  val maxSize = 512 * 1024

  var receiveBuffer = ByteString.empty

  val sendBuffer = ByteString.newBuilder

  var waitForAck = false

  val client = context.actorOf(acceptWithActor(self), "client-" + self.path.name.substring("client-session-".length))

  def receive: Receive = {
    case Received(receivedData) ⇒
      val data = receiveBuffer ++ receivedData
      val (newBuffer, frames) = extractFrames(data, Nil)
      for (frame ← frames) client ! serializer.bytesToMessage(frame.msgId, frame.byteString)
      receiveBuffer = newBuffer

    case _: ConnectionClosed ⇒
      log.debug("connection closed")
      context stop self

    case msg: GeneratedMessage ⇒ send(msg)

    case CommandFailed(e) ⇒
      log.error("command failed " + e)
      context stop self

    case Ack ⇒
      waitForAck = false
      flush()

    case CloseConnection ⇒ tcp ! Tcp.Close
  }

  case class Frame(msgId: Int, byteString: ByteString)

  @tailrec
  private def extractFrames(data: ByteString, frames: List[Frame]): (ByteString, Seq[Frame]) =
    if (data.length < headerSize)
      (data.compact, frames)
    else {
      val iterator = data.iterator
      val msgId = iterator.getInt
      val size = iterator.getInt

      if (size < 0 || size > maxSize)
        throw new IllegalArgumentException(s"received too large frame of size $size (max = $maxSize)")

      val totalSize = headerSize + size
      if (data.length >= totalSize)
        extractFrames(data drop totalSize, frames :+ Frame(msgId, data.slice(headerSize, totalSize)))
      else
        (data.compact, frames)
    }

  def send(msg: GeneratedMessage): Unit = {
    val builder = ByteString.newBuilder
    val os = builder.asOutputStream
    msg.writeDelimitedTo(os)
    val msgByteString = builder.result()

    val msgId = serializer.messageToId(msg)
    sendBuffer.putInt(msgId)
    sendBuffer.putInt(msgByteString.length)
    sendBuffer.append(msgByteString)
    flush()
  }

  def flush(): Unit =
    if (!waitForAck && sendBuffer.length > 0) {
      waitForAck = true
      tcp ! Write(sendBuffer.result.compact, Ack)
      sendBuffer.clear()
    }
}


