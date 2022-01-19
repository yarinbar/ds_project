package server

import com.google.protobuf.util.Timestamps
import com.kotlingrpc.demoGrpc.generated.main.grpckt.com.kotlingrpc.demoGrpc.HelloWorldClient
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import messages.*
import org.springframework.http.MediaType
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.collections.*

@Service
class TransactionsManager {
    val port = 50051
    val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()
    val client = HelloWorldClient(channel)

      fun sendCoins(src_addr: String, dst_addr: String, coins: UInt) : String = runBlocking {
        println("here")
        val txid = client.send_money(src_addr = src_addr, dst_addr = dst_addr, coins = coins).txId
          if (txid == "-1"){return@runBlocking "Operation failed, not enough funds."}
          else
          return@runBlocking "Success! Sent. ${coins} coins to ${dst_addr} from ${src_addr}.\nTransaction submitted with id ${txid}"
    }

    fun submitTx(tx:Tx):String = runBlocking {
        val ret = client.submitTx(tx)
        return@runBlocking (ret.txId)
    }

    fun submitAtomicTxList(tx_list: AtomicTxListRequest):String = runBlocking {
        val ret = client.submitAtomicTxList(tx_list)
        return@runBlocking (ret.txIdsListList.joinToString(", "))
    }


    fun getUtxos(addr: String,n:Int=-1):String = runBlocking {
        println("getting your utxos!")
        val utxos = client.getUtxos(addr,n).utxosList
        println("succeeded")
        println(utxos)
        return@runBlocking utxos.toString()
    }

    fun getAddrHistory(addr: String,n:Int=-1):String = runBlocking{
        println("Getting history for address ${addr}")
        val hist = client.getAddrHistory(addr,n).txsList
        println("succeeded")
        println(hist)
        return@runBlocking hist.toString()
    }
}


@RestController
class TMController(private val transactionsManager: TransactionsManager) {

    //    List the entire ledger history since the Genesis UTxO ordered by the transaction timestamps. It should also support a limit on the number of transactions being returned.
//
    @GetMapping("/utxos/{addr}")
    fun getUTxOs(@PathVariable("addr") addr: String): String? {
        return transactionsManager.getUtxos(addr)}

    @GetMapping("/utxos/{addr}/{n}")
    fun getUTxOs(@PathVariable("addr") addr: String, @PathVariable("n")n:Int): String? {
        return transactionsManager.getUtxos(addr,n)}
//
    @GetMapping("/ledger/{addr}/{n}")
    fun getLedgerHistory(@PathVariable("addr") addr: String, @PathVariable("n")n:Int): String =
        transactionsManager.getAddrHistory(addr,n)


    @GetMapping("/ledger/{addr}")
    fun getLedgerHistory(@PathVariable("addr") addr: String): String =
        transactionsManager.getAddrHistory(addr)


    @PostMapping("/sendCoins")
     fun sendCoins(@RequestParam to:String, @RequestParam  from: String, @RequestParam  coins: UInt ) : String? {
        val ret= transactionsManager.sendCoins(src_addr = from, dst_addr = to, coins = coins)
        println("GOT BACK")
        println(ret)
        return ret
    }

    @Serializable
    data class SerTr(val addr: String, val coins: Long)


    @Serializable
    data class SerUTxO(val addr: String, val tx_id: String, val coins: Long)


    @Serializable
    data class SerTx(val inputs: MutableList<SerUTxO>, val outputs: MutableList<SerTr>)

    fun serTxToGRPC(tx: SerTx): Tx{
        val inputs : MutableList<UTxO> = mutableListOf<UTxO>()
        val outputs : MutableList<Tr> = mutableListOf<Tr>()

        for (utxo in tx.inputs) {
            // not adding coins because it adds another validity check
            inputs.add(uTxO {
                txId = utxo.tx_id
                addr = utxo.addr
                coins = utxo.coins
            })
        }

        for (tr in tx.outputs) {
            outputs.add(tr {
                addr = tr.addr
                coins = tr.coins
            })
        }

        val new_tx_id = UUID.randomUUID().toString()
        val new_tx = tx {
            txId = new_tx_id
            this.inputs.addAll(inputs)
            this.outputs.addAll(outputs)
            timestamp = Timestamps.fromMillis(System.currentTimeMillis())
        }

        return new_tx
    }

    @PostMapping("/submitTx", consumes = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun submitTx(@RequestBody tx: String ) : String? {
        val decoded_tx = Json { ignoreUnknownKeys = true }.decodeFromString<SerTx>(tx)

        val new_tx = serTxToGRPC(decoded_tx)

        println("Server: submitTx: submitting the following tx request\n${new_tx}")

        val ret = transactionsManager.submitTx(new_tx)

        return ret
    }

    @Serializable
    data class SerAtomicTxList(val txs: MutableList<SerTx>)

    @PostMapping("/submitAtomicTxList", consumes = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun submitAtomicTxList(@RequestBody tx_list: String ) : String? {

        val decoded_tx_list = Json { ignoreUnknownKeys = true }.decodeFromString<SerAtomicTxList>(tx_list)
        val grpc_atomic_tx_list : MutableList<Tx> = mutableListOf()

        for (tx in decoded_tx_list.txs){
            grpc_atomic_tx_list.add(serTxToGRPC(tx))
        }

        val atomic_tx_list = atomicTxListRequest {
            txList.addAll(grpc_atomic_tx_list)
        }

        println("Server: submitAtomicTxList: submitting the following atomic tx list request\n${atomic_tx_list}")

        val atomic_list_request = atomicTxListRequest {
            txList.addAll(grpc_atomic_tx_list)
        }

        val ret = transactionsManager.submitAtomicTxList(atomic_list_request)
        return ret
    }
}
