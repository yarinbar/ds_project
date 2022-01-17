package grpc_server
import com.google.protobuf.util.Timestamps.fromMillis
import com.kotlingrpc.demoGrpc.generated.main.grpckt.com.kotlingrpc.demoGrpc.HelloWorldClient
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import messages.*
import java.util.*
import kotlin.collections.HashMap
import org.apache.zookeeper.Watcher
import org.apache.zookeeper.ZooKeeper
import zookeeper.kotlin.ZKPaths
import zookeeper.kotlin.ZookeeperKtClient
import java.net.InetAddress
import zookeeper.kotlin.createflags.Ephemeral
import zookeeper.kotlin.createflags.Sequential
import zookeeper.kotlin.createflags.Persistent


class HelloWorldServer(private val ip: String, private val shard: Int, private val port: Int) {
    var utxos: HashMap<String, MutableList<UTxO>> = HashMap()
    var ledger: HashMap<String, MutableList<Tx>> = HashMap()
    var my_shard: Int = shard
    var num_shards: Int = System.getenv("NUM_SHARDS").toInt()
    var my_ip: String = ip

    val server: Server = ServerBuilder
        .forPort(port)
        .addService(UserServices())
        .build()

    fun get_zk():ZooKeeper{
        var zk_host = InetAddress.getByName("zoo1.zk.local")
        println("This is the zk address ${zk_host.hostAddress}")
        var zkConnectionString = "${zk_host.hostAddress}:2181"
        println("--- Connecting to ZooKeeper @ $zkConnectionString")
        val chan = Channel<Unit>()
        val zk = ZooKeeper(zkConnectionString, 1000) { event ->
            if (event.state == Watcher.Event.KeeperState.SyncConnected &&
                event.type == Watcher.Event.EventType.None
            ) {
                runBlocking { chan.send(Unit) }
            }
        }
        println("--- Connected to ZooKeeper")
        return zk
    }
    val zk = get_zk()
    val zkc =  ZookeeperKtClient(zk)

    fun get_shard_leader1(shard_incharge: Int) : String {
        println("trying to locate the correct shard's leader (shard ${shard_incharge})")
        val path="/SHARD_${shard_incharge}"
        val children = zk.getChildren(path,false)
            .sortedBy { ZKPaths.extractSequentialSuffix(it)!! }
        var delimiter = "_"
        return children.first().toString().split(delimiter)[0]
    }

    suspend fun start() {
        server.start()
        println("registering with ZK")

        println("My ip is ${this.my_ip}")

        val existing_shards = zkc.getChildren("/").first
        if ("SHARD_${my_shard}" !in existing_shards)
            {
            println("Shard ${this.my_shard} doesn't exist, creating it")
            val (_, _) = zkc.create {
                path = "/SHARD_${my_shard}"
                flags = Persistent}
            }
        println("Joining shard ${this.my_shard}.")
        val (_, _) = zkc.create {
            path = "/SHARD_${my_shard}/${my_ip}_"
            flags = Ephemeral and Sequential}
        println("DONE registering with ZK")

        println("Server started, listening on $port")


        // genesis
        if (my_shard == 0) {
            println("Server for shard 0, creating genesis UTxO")
            val tx_id = "0x00000000001"
            val addr = "0000"
            val new_utxo = uTxO {
                this.txId = tx_id
                this.addr = addr
                this.coins = 100
            }

            utxos.put(addr, mutableListOf(new_utxo))
            println("Created genesis UTxO")
        }

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

    inner class UserServices : UserServicesGrpcKt.UserServicesCoroutineImplBase() {

        fun find_addr_shard(addr: String): Int {
            val int_addr = addr.toBigInteger()
            return int_addr.mod(num_shards.toBigInteger()).toInt()
        }

//        override suspend fun getHhistory(request: HistoryRequest): HistoryResponse {
//            val addr = request.addr
//            val ledger_hist: List<Tx>
//            if (addr == "") {
//                ledger_hist = ledger.values.toList().flatten()
//            } else {
//                val addr_history = ledger.get(addr)
//
//                if (addr_history != null)
//                    ledger_hist = addr_history.toList()
//                else
//                    ledger_hist = emptyList()
//            }
//
//            val grpc_ledger_hist = historyResponse {
//                txs.addAll(ledger_hist)
//            }
//
//            return grpc_ledger_hist
//        }

        override suspend fun getHistory(request: HistoryRequest):HistoryResponse{
            println("GET HISTORY SERVER")
//            val shard_incharge = find_addr_shard(request.srcAddr)
            val shard_leader = get_shard_leader1(find_addr_shard(request.addr))
            if (my_ip == shard_leader) {
                println("Correct node, handling request")
                return getHistoryImp(request)
            }
            println("Wrong shard or not the leader! send to address ${shard_leader}")
            val target_ip = shard_leader
            val channel = ManagedChannelBuilder.forAddress(target_ip, 50051).usePlaintext().build()
            val client = HelloWorldClient(channel)
            return client.getAddrHistory(request.addr,request.limit.toInt())
        }

        fun getHistoryImp(request: HistoryRequest):HistoryResponse{

            val addr = request.addr
            println(addr)
            var tx_hist = emptyList<Tx>()
            println(tx_hist)
            val addr_history = ledger[addr]
            println("ADDR HIST")
            println(addr_history)
            if (addr_history != null){
                tx_hist = addr_history.toList()
                if (request.limit >=0 ){
                    println(tx_hist)
                    tx_hist = tx_hist.take(request.limit.toInt())
                    println(tx_hist)
                }
            }

            val grpc_tx_hist = historyResponse {
                txs.addAll(tx_hist)
            }
            println("HERE")
            return grpc_tx_hist
        }
        override suspend fun getUTxOs(request: UTxORequest): UTxOResponse {

            println("GET UTXO SERVER")
//            val shard_incharge = find_addr_shard(request.srcAddr)
            val shard_leader = get_shard_leader1(find_addr_shard(request.addr))
            if (my_ip == shard_leader) {
                println("Correct node, handling request")
                return getUTxOsImp(request)
            }
            println("Wrong shard or not the leader! send to address ${shard_leader}")
            val target_ip = shard_leader
            val channel = ManagedChannelBuilder.forAddress(target_ip, 50051).usePlaintext().build()
            val client = HelloWorldClient(channel)
            return client.getUtxos(request.addr,request.limit.toInt())
        }


        fun getUTxOsImp(request: UTxORequest): UTxOResponse {
            val addr = request.addr
            println(addr)
            var utxos_hist = emptyList<UTxO>()
            println(utxos_hist)
            val addr_history = utxos[addr]
            println("ADDR HIST")
            println(addr_history)
            if (addr_history != null){
                utxos_hist = addr_history.toList()
                if (request.limit >=0 ){
                    println(utxos_hist)
                    utxos_hist = utxos_hist.take(request.limit.toInt())
                    println(utxos_hist)
                }
            }

            val grpc_utxos_hist = uTxOResponse {
                utxos.addAll(utxos_hist)
            }
            println("HERE")
            return grpc_utxos_hist
        }

        override suspend fun sendMoney(request: SendMoneyRequest): SendMoneyResponse {
            println("SEND MONEY SERVER")
//            val shard_incharge = find_addr_shard(request.srcAddr)
            val shard_leader = get_shard_leader1(find_addr_shard(request.srcAddr))
            if (my_ip == shard_leader) {
                println("Correct node, handling request")
                return sendMoneyImp(request)
            }
            println("Wrong shard or not the leader! send to address ${shard_leader}")
            val target_ip = shard_leader
            val channel = ManagedChannelBuilder.forAddress(target_ip, 50051).usePlaintext().build()
            val client = HelloWorldClient(channel)
            return client.send_money(request.srcAddr, request.dstAddr, request.coins.toUInt())
        }

        override suspend fun sendInducedUTxO(request: UTxO): InternalResponse {
            println("adding utxo to right shard")
            println(request)
            if (utxos.containsKey(request.addr)) {
                utxos[request.addr]?.add(request)
            } else {
                utxos[request.addr] = mutableListOf(request)
            }
            println("UTXOS for ${request.addr} after transaction")
            println(utxos)
            return internalResponse { status=1}
        }

        suspend fun sendMoneyImp(request: SendMoneyRequest): SendMoneyResponse {
            println("SEND MONEY IMP")
            val src_addr = request.srcAddr
            val dst_addr = request.dstAddr
            val coins_ = request.coins.toULong()
            var grpc_send_money_response = sendMoneyResponse {
                txId = "-1"
            }
            //TODO (optional) find best utxos not just any utxos
            val available_utxos = utxos[src_addr]
            println("### ledger after transaction ###")
            println(ledger)
            println("### src available utxos before the transaction###")
            println(available_utxos)
            var sumUTxOs = 0.toULong()
            var utxo_list = mutableListOf<UTxO>()
            if (available_utxos != null) {
                for (utxo in available_utxos) {
                    sumUTxOs += utxo.coins.toULong()
                    utxo_list.add(utxo)
                    if (sumUTxOs >= coins_)
                        break
                }
            }
            if (sumUTxOs < coins_) {
                println(coins_)
                print("Not enough funds for this")
                return grpc_send_money_response
            }
            println("### utxos that will be used in the transaction ###")
            println(utxo_list)
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
            if (ledger.containsKey(src_addr)) {
                ledger[src_addr]?.add(new_tx)
            } else {
                ledger[src_addr] = mutableListOf(new_tx)
            }
            println("### ledger after transaction ###")
            println(ledger)

            // save new induced utxos at dest utxos
            for (tr in transfers) {
                val induced_utxo = uTxO {
                    txId = tx_id
                    addr = tr.addr
                    coins = tr.coins
                }
                if (find_addr_shard(induced_utxo.addr)==find_addr_shard(src_addr)) {
//                     TODO send to leader??
                    println("Induced utxo in same shard, adding it")
                    // add to utxo hashmap, check if addr is already mapped first
                    if (utxos.containsKey(tr.addr)) {
                        println("adding to existing")
                        utxos[tr.addr]?.add(induced_utxo)
                    } else {
                        println("no existing, creating new entry!!")
                        utxos[tr.addr] = mutableListOf(induced_utxo)
                    }

                } else {
                    println("Induced utxo in another shard, adding it there")
                    val target_ip = get_shard_leader1(find_addr_shard(induced_utxo.addr))
                    val channel = ManagedChannelBuilder.forAddress(target_ip, 50051).usePlaintext().build()
                    val client = HelloWorldClient(channel)
                    client.send_induced_utxo(induced_utxo)
                }
            }

            // remove used utxos
            for (utxo in utxo_list)
                utxos[src_addr] = utxos[src_addr]!!.minus(utxo).toMutableList()

            println("### UTXOS after the tx ###")
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
}
