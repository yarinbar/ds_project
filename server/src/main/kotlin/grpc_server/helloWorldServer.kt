package grpc_server


import com.google.protobuf.util.Timestamps.fromMillis
import com.kotlingrpc.demoGrpc.generated.main.grpckt.com.kotlingrpc.demoGrpc.HelloWorldClient
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import messages.*
import java.util.*
import kotlin.collections.HashMap
import zookeeper.kotlin.ZookeeperKtClient
import kotlinx.coroutines.runBlocking
import zookeeper.kotlin.*
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.data.Stat
import java.net.InetAddress


class HelloWorldServer(private val ip: String, private val shard: Int, private val port: Int) {
    var utxos: HashMap<String, MutableList<UTxO>> = HashMap()
    var ledger: HashMap<String, MutableList<Tx>> = HashMap()
    var my_shard : Int = shard
    var num_shards : Int = System.getenv("NUM_SHARDS").toInt()
    var my_ip : String = ip

    // TODO - check this

    var zk_host = InetAddress.getByName("zoo1.zk.local")
    var zk = ZooKeeper("${zk_host.hostAddress}:2181", 1000, null)
//    var zkc = ZookeeperKtClient(zk)

    val server: Server = ServerBuilder
        .forPort(port)
        .addService(UserServices())
        .addService(InternalServices())
        .build()

    fun start() {
        println("This is the zk address ${this.zk_host.hostAddress}")
        server.start()

        println("My ip is ${this.my_ip}")
        println("Joining shard ${this.my_shard}")
        println("Server started, listening on $port")

        println("Creating genesis UTxO")

        val tx_id = "0x00000000001"
        val addr = "0000"

        println("registering with ZK")
        val create_op = CreateOperation("${my_shard}/${my_ip}", CreateFlags.Ephemeral)
        zk.create("/${my_shard}/${my_ip}", create_op.data, create_op.acl, create_op.flags.zkCreateMode, Stat())!!
//        zkc.create(a)
        println("DONE registering with ZK")
        // genesis
        val new_utxo = uTxO {
            this.txId = tx_id
            this.addr = addr
            this.coins = 100
        }

        utxos.put(addr, mutableListOf(new_utxo))
        println("Created genesis UTxO")


        Runtime.getRuntime().addShutdownHook(
            Thread {
                println("*** shutting down gRPC server since JVM is shutting down")
                this@HelloWorldServer.stop()
                println("*** server shut down")
            }
        )
    }

    private fun stop() {
        server.shutdown()
    }

    fun blockUntilShutdown() {
        server.awaitTermination()
    }

    inner class UserServices : UserServicesGrpcKt.UserServicesCoroutineImplBase(){

        fun find_addr_shard(addr : String) : Int{
            val int_addr = addr.toBigInteger()
            return int_addr.mod(num_shards.toBigInteger()).toInt()
        }

        override suspend fun getHistory(request: HistoryRequest): HistoryResponse {

            val addr = request.addr

            val ledger_hist: List<Tx>

            if (addr == ""){
                ledger_hist = ledger.values.toList().flatten()
            }

            else{
                val addr_history = ledger.get(addr)

                if(addr_history != null)
                    ledger_hist = addr_history.toList()
                else
                    ledger_hist = emptyList()
            }

            val grpc_ledger_hist = historyResponse {
                txs.addAll(ledger_hist)
            }

            return grpc_ledger_hist
        }

        override suspend fun getUTxOs(request: UTxORequest): UTxOResponse {
            val addr = request.addr
            val utxos_hist: List<UTxO>
            val addr_history = utxos.get(addr)
            if(addr_history != null)
                utxos_hist = addr_history.toList()
            else
                utxos_hist = emptyList()

            val grpc_utxos_hist = uTxOResponse {
                utxos.addAll(utxos_hist)
            }
            return grpc_utxos_hist
        }
        override suspend fun sendMoney(request: SendMoneyRequest): SendMoneyResponse {
            println("SEND MONEY SERVER")

            if (my_shard == find_addr_shard( request.srcAddr)) {
                return sendMoneyImp(request)
            }
            val target_ip = "172.17.0.3" // GET FROM ZOOKEEPER
            val channel = ManagedChannelBuilder.forAddress(target_ip, 50051).usePlaintext().build()
            val client = HelloWorldClient(channel)
            return client.send_money(request.srcAddr,request.dstAddr,request.coins.toUInt())
        }

        fun sendMoneyImp(request: SendMoneyRequest): SendMoneyResponse {
            println("SEND MONEY IMP")

            println("HERE!")
            val src_addr = request.srcAddr
            val dst_addr = request.dstAddr
            val coins_ = request.coins.toULong()
            println(src_addr)
            var grpc_send_money_response = sendMoneyResponse {
                txId = "-1"
            }

            // If Leader in correct shard
            val available_utxos = utxos[src_addr]

            println(available_utxos)

            var sumUTxOs = 0.toULong()
            var utxo_list = mutableListOf<UTxO>()

            if (available_utxos != null) {
                println("available utxos")
                for (utxo in available_utxos) {
                    println(utxo)
                    sumUTxOs += utxo.coins.toULong()
                    utxo_list.add(utxo)
                    if (sumUTxOs >= coins_)
                        break
                }
            }

            if (sumUTxOs < coins_) {
                println(coins_)
                print("oops")
                return grpc_send_money_response
            }
            val tx_id = UUID.randomUUID().toString()

            val transfers = mutableListOf<Tr>()

            transfers.add(tr {
                addr = dst_addr
                coins = coins_.toLong()
            })

            if (sumUTxOs != coins_) {
                transfers.add(
                    tr {
                        addr = src_addr
                        coins = (sumUTxOs - coins_).toLong()
                    }
                )
            }

            // add to ledger in current shard
            val new_tx = tx {
                txId = tx_id
                inputs.addAll(utxo_list)
                outputs.addAll(transfers)
                timestamp = fromMillis(System.currentTimeMillis())
            }
            if (ledger.containsKey(src_addr)){
                ledger.get(src_addr)?.add(new_tx)
            }
            else {
                ledger.put(src_addr, mutableListOf(new_tx))
            }

            // save new induced utxos at dest utxos
            for (tr in transfers) {

                // TODO -need to send to relevant shards if dest!=src
                val induced_utxo = uTxO {
                    txId = tx_id
                    addr = tr.addr
                    coins = tr.coins
                }

                // TODO - fix this
                if (shard == shard) {
                    // add to utxo hashmap, check if addr is already mapped first
                    if (utxos.containsKey(tr.addr)) {
                        utxos.get(tr.addr)?.add(induced_utxo)
                    } else {
                        utxos.put(tr.addr, mutableListOf(induced_utxo))
                    }
                } else {
                    // TODO - transfer to correct shard
                }

            }

            //remove used utxos
            if (available_utxos != null) {
                var utxo_to_delete = mutableListOf<UTxO>()
                for (utxo in available_utxos){
                    if (utxo.txId != tx_id)
                        utxo_to_delete.add(utxo)
                }
                for (utxodel in utxo_to_delete){
                    utxos.get(src_addr)?.remove(utxodel)
                }
            }

            println(utxos)

            grpc_send_money_response = sendMoneyResponse {
                txId = tx_id
            }
            return grpc_send_money_response
        }

        override suspend fun submitTx(request: Tx): SendMoneyResponse {
            return super.submitTx(request)
        }
    }

    inner class InternalServices : InternalServicesGrpcKt.InternalServicesCoroutineImplBase(){
        override suspend fun sendInducedUTxO(request: UTxO): InternalResponse {
            return super.sendInducedUTxO(request)
        }
    }
}
