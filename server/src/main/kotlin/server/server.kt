package server

import com.kotlingrpc.demoGrpc.generated.main.grpckt.com.kotlingrpc.demoGrpc.HelloWorldClient
import grpc_server.HelloWorldServer
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*

@Service
class TransactionsManager {
    val port = 50051
    val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()
    val client = HelloWorldClient(channel)

      fun sendCoins(src_addr: String, dst_addr: String, coins: UInt) : String = runBlocking {
        println("here")
        val txid = client.send_money(src_addr = src_addr, dst_addr = dst_addr, coins = coins)
          return@runBlocking txid
    }
}


@RestController
class TMController(private val transactionsManager: TransactionsManager) {

    //    List the entire ledger history since the Genesis UTxO ordered by the transaction timestamps. It should also support a limit on the number of transactions being returned.
//
//    @GetMapping("/utxos/{addr}")
//    fun getUTxOs(@PathVariable("addr") addr: String): MutableList<UTxO>? =
//        transactionsManager.getUtxos(addr)
//
//    @GetMapping("/ledger/{addr}/{n}")
//    fun getLedgerHistory(@PathVariable("addr") addr: String, @PathVariable("n")n:Int): List<Tx>? =
//        transactionsManager.getAddrHistory(addr,n)
//
//
//    @GetMapping("/ledger/{addr}")
//    fun getLedgerHistory(@PathVariable("addr") addr: String): List<Tx>? =
//        transactionsManager.getAddrHistory(addr)


    @PostMapping("/sendCoins")
     fun sendCoins(@RequestParam to:String, @RequestParam  from: String, @RequestParam  coins: UInt ) : String? {
        return transactionsManager.sendCoins(src_addr = from, dst_addr = to, coins = coins)
    }


//    @PostMapping("/submitTx")
//    fun submitTx(@RequestBody tx: Tx ) : Tx? =
//        transactionsManager.submitTx(tx)
}
