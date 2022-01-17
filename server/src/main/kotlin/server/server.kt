package server

import com.kotlingrpc.demoGrpc.generated.main.grpckt.com.kotlingrpc.demoGrpc.HelloWorldClient
import grpc_server.HelloWorldServer
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import messages.*

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
        println("submitting tx")
        return@runBlocking ("cool")
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


//    @PostMapping("/submitTx")
//    fun submitTx(@RequestBody tx: Tx ) : String? {
//        return transactionsManager.submitTx(tx)
//    }
}
