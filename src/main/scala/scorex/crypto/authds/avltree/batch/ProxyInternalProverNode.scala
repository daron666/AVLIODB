package scorex.crypto.authds.avltree.batch

import io.iohk.iodb.Store
import scorex.crypto.authds.avltree.{AVLKey, Balance}
import scorex.crypto.hash.ThreadUnsafeHash


@specialized
class ProxyInternalProverNode(protected var pk: AVLKey,
                              val lkey: AVLKey,
                              val rkey: AVLKey,
                              protected var pb: Balance = 0.toByte)
                             (implicit val phf: ThreadUnsafeHash,
                              store: Store,
                              nodeParameters: NodeParameters)
  extends InternalProverNode(k = pk, l = null, r = null, b = pb)(phf) {

  override def left: ProverNodes = {
    if (l == null) VersionedIODBAVLStorage.fetch(lkey) else l
  }

  override def right: ProverNodes =
    if (r == null) VersionedIODBAVLStorage.fetch(rkey) else r
}