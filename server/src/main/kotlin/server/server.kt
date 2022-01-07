package server

import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestBody
import java.math.BigInteger
import java.sql.Timestamp
import java.util.*

fun Int.toBigInteger() = BigInteger.valueOf(toLong())
var id = 1.toBigInteger()
class TransferRequest(
    val from: String,
    val to: String,
    val coins: ULong
)

data class UTxO(
    val txId: BigInteger,
    val address: String,
)

data class UTxOWithValue(
    val uTxO: UTxO,
    val value: ULong
)

data class Tr (
    val address: String,
    val coins: ULong
)


data class Tx(
    val txId: BigInteger,
    val inputs: List<UTxO>,
    val outputs: List<Tr>
//    val timestamp: Timestamp,
)

data class TxWithTime(
    val tx: Tx,
    val timestamp: Timestamp
)
@Service
class TransactionsManager{
    var utxos: HashMap<String, MutableList<UTxOWithValue>> =HashMap<String,MutableList<UTxOWithValue>> ()
    var ledger: HashMap<String, MutableList<TxWithTime>> = HashMap<String,MutableList<TxWithTime>> ()

    fun addTx(tx:Tx):Boolean {
        return true
    }
    fun addGenesisTx(): Unit {
        val genesisCoins = 100.toULong()
        val genesisAddr = 0.toString()
        val genesisTr = Tr(genesisAddr,genesisCoins)
        val timestamp = Timestamp(System.currentTimeMillis())
        val genesisTx = Tx(0.toBigInteger(), emptyList(), listOf(genesisTr))
        val genesisTxWithTime = TxWithTime(genesisTx,timestamp)
        val genesisUTxO = UTxO (0.toBigInteger(),genesisAddr)
        val genesosUTxOwval = UTxOWithValue(genesisUTxO,genesisCoins)
        utxos.put(genesisUTxO.address,mutableListOf(genesosUTxOwval))
        ledger.put(genesisAddr,mutableListOf(genesisTxWithTime))  // add timestamp
    }

    fun getUtxos(address: String): MutableList<UTxO>? {
        //If in correct shard (doesn't have to be leader)
        val addr_utxos = utxos.get(address)
        var utxos_no_val = mutableListOf<UTxO>()
        if (addr_utxos != null) {
            addr_utxos.forEach({i -> utxos_no_val.add(i.uTxO)})
        }
        return utxos_no_val
    }

    //need to add support for limit
    fun getAddrHistory(address: String,limit: Int?=0) : List<Tx> {
        // if in correct shard (doesn't have to be leader)
        val addrTxs = ledger[address]
        var txs_no_time = mutableListOf<Tx>()
        if (addrTxs != null) {
            addrTxs.sortWith(compareByDescending { it.timestamp })
            addrTxs.forEach({i -> txs_no_time.add(i.tx)})
        }
        if (limit!=null && limit>0)
            return txs_no_time.take(limit)
        return txs_no_time
    }

    fun sendCoins(to:String,from:String,coins:ULong) : BigInteger? {
        // If Leader in correct shard
        val available_utxos = utxos.get(from)
        println(available_utxos)
        var sumUTxOs = 0.toULong()
        var utxo_values = mutableListOf<UTxOWithValue>()
        var utxo_list = mutableListOf<UTxO>()
        if (available_utxos != null) {
            for (utxowval in available_utxos) {
                sumUTxOs+=utxowval.value
                utxo_values.add(utxowval)
                utxo_list.add(utxowval.uTxO)
                if (sumUTxOs >= coins)
                    break
            }
        }
        if (sumUTxOs < coins) {
            return null
        }
        val tx_id = id++ //generate id
        val transfers = mutableListOf<Tr>()
        transfers.add(Tr(to,coins))
        if (sumUTxOs != coins) {
            transfers.add(Tr(from, (sumUTxOs - coins)))
        }
        // add to ledger in current shard
        val newTx = TxWithTime(Tx(tx_id,utxo_list,transfers),Timestamp(System.currentTimeMillis()))
        if (ledger.containsKey(from)){
            ledger.get(from)?.add(newTx)
        }
        else {
            ledger.put(from,mutableListOf(newTx))
        }

        // save new induced utxos at dest utxos
        for (tr in transfers){
            //need to send to relevant shards if dest!=src
            val induced_utxo_w_val = UTxOWithValue(UTxO(tx_id,tr.address),tr.coins)
            // add to utxo hashmap, check if addr is already mapped first
            if (utxos.containsKey(tr.address)){
                utxos.get(tr.address)?.add(induced_utxo_w_val)
            }
            else {
                utxos.put(tr.address,mutableListOf(induced_utxo_w_val))
            }
        }

        //remove used utxos
        print(available_utxos)
        if (available_utxos != null) {
            var utxo_to_delete = mutableListOf<UTxOWithValue>()
            for (utxo in available_utxos){
                if (utxo.uTxO.txId != tx_id)
                    utxo_to_delete.add(utxo)
            }
            for (utxodel in utxo_to_delete){
                utxos.get(from)?.remove(utxodel)
            }
        }
        return tx_id
    }

    fun submitTx(tx:Tx):Tx{
        //if leader in correct shard
        var tr_sum = 0
        var tx_sum = 0
        // do we need to check if sum(tr)=sum(tx) or just if these utxos are available?
        //generate id?

        return tx
    }
}



@RestController
class TMController(private val transactionsManager: TransactionsManager) {

    //    List the entire ledger history since the Genesis UTxO ordered by the transaction timestamps. It should also support a limit on the number of transactions being returned.

    @PostMapping("/genesis")
    fun insertGenesis(): Unit =
        transactionsManager.addGenesisTx()

    @GetMapping("/utxos/{addr}")
    fun getUTxOs(@PathVariable("addr") addr: String): MutableList<UTxO>? =
        transactionsManager.getUtxos(addr)

    @GetMapping("/ledger/{addr}/{n}")
    fun getLedgerHistory(@PathVariable("addr") addr: String, @PathVariable("n")n:Int): List<Tx>? =
        transactionsManager.getAddrHistory(addr,n)


    @GetMapping("/ledger/{addr}")
    fun getLedgerHistory(@PathVariable("addr") addr: String): List<Tx>? =
        transactionsManager.getAddrHistory(addr)


    @PostMapping("/sendCoins")
    fun sendCoins(@RequestParam to:String , @RequestParam  from: String, @RequestParam  coins: ULong ) : BigInteger? =
        transactionsManager.sendCoins(to,from,coins)

    @PostMapping("/submitTx")
    fun submitTx(@RequestBody tx: Tx ) : Tx? =
        transactionsManager.submitTx(tx)
}