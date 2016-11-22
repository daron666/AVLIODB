package io.iohk.avliodb

import com.google.common.primitives.Longs
import io.iohk.iodb.{ByteArrayWrapper, Store}
import scorex.crypto.authds.avltree._
import scorex.crypto.authds.avltree.batch.VersionedAVLStorage
import scorex.crypto.encode.Base58
import scorex.crypto.hash.{Blake2b256, ThreadUnsafeHash}

import scala.util.Try

class VersionedIODBAVLStorage(store: Store,
                              keySize: Int = 26,
                              valueSize: Int = 8,
                              labelSize: Int = 32)(implicit val hf: ThreadUnsafeHash) extends VersionedAVLStorage {

  private val TopNodeKey: ByteArrayWrapper = ByteArrayWrapper(Array.fill(labelSize)(123: Byte))
  println("???" + store.lastVersion)


  override def update(topNode: ProverNodes): Try[Unit] = Try {
    //TODO topNode is a special case?
    val topNodePair = (nodeKey(topNode), ByteArrayWrapper(toBytes(topNode)))
    lastVersion = lastVersion + 1
    val indexes: Seq[(ByteArrayWrapper, ByteArrayWrapper)] =
      Seq((TopNodeKey, nodeKey(topNode)),
        (versionsReverseKey(lastVersion), ByteArrayWrapper(topNode.label)),
        (versionsKey(topNode.label), ByteArrayWrapper(Longs.toByteArray(lastVersion))))
    val toInsert = topNodePair +: serializedVisitedNodes(topNode)
    require(toInsert.map(_._1).contains(nodeKey(topNode)))

    println("Update: " + Base58.encode(topNode.label) + " | " + lastVersion)
    println(toInsert.map(_._1).map(d => Base58.encode(d.data)))


    //TODO to remove?
    store.update(longVersion(topNode.label), Seq(), indexes ++ toInsert)

    version
  }

  override def rollback(version: Version): Try[ProverNodes] = Try {
    lastVersion = versions(version)
    println("Rollback: " + Base58.encode(version) + " | " + lastVersion)

    store.rollback(lastVersion)
    def recover(key: Array[Byte]): ProverNodes = {
      println("recover: " + Base58.encode(key) + " = " + store.get(ByteArrayWrapper(key)))
      val bytes = store.get(ByteArrayWrapper(key)).data
      bytes.head match {
        case 0 =>
          val balance = bytes.slice(1, 2).head
          val key = bytes.slice(2, 2 + keySize)
          val left = recover(bytes.slice(2 + keySize, 2 + keySize + labelSize))
          val right = recover(bytes.slice(2 + keySize + labelSize, 2 + keySize + (2 * labelSize)))
          ProverNode(key, left, right, balance)
        case 1 =>
          val key = bytes.slice(1, 1 + keySize)
          val value = bytes.slice(1 + keySize, 1 + keySize + valueSize)
          val nextLeafKey = bytes.slice(1 + keySize + valueSize, 1 + (2 * keySize) + valueSize)
          Leaf(key, value, nextLeafKey)
      }
    }
    val r = recover(store.get(TopNodeKey).data)
    println("~!!!@!@!@")
    println(Base58.encode(r.label))
    println(Base58.encode(version))
    r
  }

  override def version: Version = versionsReverse(store.lastVersion)

  override def isEmpty: Boolean = {
    store.lastVersion == 0
  }


  private def serializedVisitedNodes(node: ProverNodes): Seq[(ByteArrayWrapper, ByteArrayWrapper)] = {
    //TODO visited or isNew?
    if (node.visited || node.isNew) {
      //    if (true) {
      val pair: (ByteArrayWrapper, ByteArrayWrapper) = (nodeKey(node), ByteArrayWrapper(toBytes(node)))
      node match {
        case n: ProverNode =>
          val leftSubtree = serializedVisitedNodes(n.left)
          val rightSubtree = serializedVisitedNodes(n.right)
          pair +: (leftSubtree ++ rightSubtree)
        case n: Leaf => Seq(pair)
      }
    } else Seq()
  }

  //TODO label or key???
  private def nodeKey(node: ProverNodes): ByteArrayWrapper = ByteArrayWrapper(node.label)


  //TODO remove when version will be Array[Byte]
  private val InitV = Array.fill(labelSize)(0: Byte)
  private var lastVersion = store.lastVersion

  private def versions(l: Array[Byte]): Long = Longs.fromByteArray(store.get(versionsKey(l)).data)

  private def versionsKey(l: Array[Byte]): ByteArrayWrapper =
    ByteArrayWrapper(Blake2b256("Versions".getBytes ++ l).take(labelSize))

  private def versionsReverseKey(l: Long): ByteArrayWrapper =
    ByteArrayWrapper(Blake2b256("Rev".getBytes ++ Longs.toByteArray(l)).take(labelSize))

  private def versionsReverse(l: Long): Array[Byte] = store.get(versionsReverseKey(l)).data

  private def longVersion(b: Version): Long = {
    lastVersion
  }

  private def toBytes(node: ProverNodes): Array[Byte] = node match {
    case n: ProverNode => (0: Byte) +: n.balance +: (n.key ++ n.leftLabel ++ n.rightLabel)
    case n: Leaf => (1: Byte) +: (n.key ++ n.value ++ n.nextLeafKey)
  }
}