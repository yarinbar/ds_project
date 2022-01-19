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
import java.util.Collections
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
            var tx_hist = emptyList<Tx>()
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
            val utxo_list = mutableListOf<UTxO>()
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

            val new_tx = tx {
                txId = tx_id
                inputs.addAll(utxo_list)
                outputs.addAll(transfers)
                timestamp = fromMillis(System.currentTimeMillis())
            }

            return submitTx(new_tx)
        }

        override suspend fun submitTx(request: Tx): SendMoneyResponse {
            // TODO - check if we need to check for validity

            val inputs = request.inputsList

            if (inputs.size == 0)
                return sendMoneyResponse {
                    txId = "tx needs to include at least 1 utxo"
                }

            val utxo_src_addr = request.inputsList[0].addr
            val target_shard = find_addr_shard(utxo_src_addr)
            val shard_leader = get_shard_leader1(target_shard)

            // -------------- Checking if the UTxOs provided are present -------------
            // Option 1 - we are in the correct shard so we can check this address
            if (my_ip == shard_leader){
                println("HelloWorldServer: submitTx: My shard and I am the leader - processing request")
                return submitTxImp(request)
            }
            // option 2 - we are not in the correct shard and need to forward the request
            else {
                println("HelloWorldServer: submitTx: Not my shard or not leader in my shard - not my problem")
                val channel = ManagedChannelBuilder.forAddress(shard_leader, 50051).usePlaintext().build()
                val client = HelloWorldClient(channel)
                return client.submitTx(request)
            }
        }

        suspend fun submitTxImp(request: Tx): SendMoneyResponse{

            val inputs = request.inputsList
            val src_addr = request.inputsList[0].addr

            val validation_res = validateTx(request)

            // If tx is not valid for any reason, return the issue
            if (validation_res != request.txId)
                return sendMoneyResponse {
                    txId = validation_res
                }

            // add to ledger in current shard
            if (ledger.containsKey(src_addr)) {
                ledger[src_addr]?.add(request)
            } else {
                ledger[src_addr] = mutableListOf(request)
            }

            println("### ledger after transaction ###")
            println(ledger)

            // save new induced utxos at dest utxos
            for (tr in request.outputsList) {
                val induced_utxo = uTxO {
                    txId = request.txId
                    addr = tr.addr
                    coins = tr.coins
                }
                if (find_addr_shard(induced_utxo.addr)==find_addr_shard(src_addr)) {
                    // TODO send to leader??
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
            for (utxo in inputs) {
                println("removing ${utxo} from ${src_addr}")
                utxos[src_addr] = utxos[src_addr]!!.minus(utxo).toMutableList()
                println("after removal ${utxos[src_addr]}")
            }

            println("### UTXOS after the tx ###")
            println(utxos)

            println("HelloWorldServer: submitTxImp: Done successfully!")
            return sendMoneyResponse {
                txId = request.txId
            }

        }

        fun validateTx(request: Tx): String{

            val inputs = request.inputsList
            val outputs = request.outputsList
            val utxo_src_addr = request.inputsList[0].addr
            var found_all_utxos : Boolean = true
            val user_utxos = utxos[utxo_src_addr]

            println("HelloWorldServer: submitTxImp: UTxOs found for address ${utxo_src_addr}:\n${user_utxos}")

            val absent_utxos : MutableList<UTxO> = mutableListOf()

            println("HelloWorldServer: submitTxImp: Beginning to process request")

            // TODO - outputs to induced
            if (user_utxos == null)
                return "no utxos found for ${utxo_src_addr}"

            for (utxo in inputs){
                if (utxo !in user_utxos){
                    found_all_utxos = false
                    absent_utxos.add(utxo)
                }
            }
            if (!found_all_utxos)
                return "the following utxos were not found and the action failed:\n${absent_utxos}"

            val inputs_tx_ids = inputs.map { it.txId }
            val relevant_utxos = utxos[utxo_src_addr]?.filter { it.txId in  inputs_tx_ids}?.map { it.coins }
            val output_coins = outputs.map { it.coins }.sum()
            val relevant_utxo_sum : Long = relevant_utxos?.sum() ?: 0

            if (output_coins != relevant_utxo_sum)
                return "utxo sum must match outputs sum, utxo sum: ${relevant_utxo_sum} output sum: ${output_coins}"

            // if all checks pass returns the tx_id
            return request.txId
        }

        override suspend fun submitAtomicTxList(request: AtomicTxListRequest): AtomicTxListResponse {
            if (!validateAtomicTxList(request))
                return atomicTxListResponse {
                    txIdsList.addAll(listOf(sendMoneyResponse { txId = "not valid atomic tx list" }))
                }

            val response_list : MutableList<SendMoneyResponse> = mutableListOf()

            for (tx in request.txListList)
                response_list.add(submitTx(tx))

            return atomicTxListResponse { txIdsList.addAll(response_list) }
        }

        suspend fun validateAtomicTxList(request: AtomicTxListRequest) : Boolean {
            val all_utxos_required : List<UTxO> = request.txListList.map { it.inputsList }.flatten()
            println("all inputs are\n${all_utxos_required}")

            // Checking that all utxos used are present
            for (utxo in all_utxos_required.distinct()){
                if (Collections.frequency(all_utxos_required, utxo) > queryUTxO(utxo).occurrences)
                    return false
            }

            return true

        }

        override suspend fun queryUTxO(request: UTxO): QueryUTxOResponse{

            val src_addr = request.addr
            val target_shard = find_addr_shard(src_addr)
            val shard_leader = get_shard_leader1(target_shard)

            // -------------- Checking if the UTxOs provided are present -------------
            // Option 1 - we are in the correct shard so we can check this address
            if (my_ip == shard_leader){
                println("HelloWorldServer: queryUTxO: My shard and I am the leader - processing request")
                return queryUTxOResponse { occurrences = queryUTxOImp(request) }
            }
            // option 2 - we are not in the correct shard and need to forward the request
            else {
                println("HelloWorldServer: queryUTxO: Not my shard or not leader in my shard - not my problem")
                val channel = ManagedChannelBuilder.forAddress(shard_leader, 50051).usePlaintext().build()
                val client = HelloWorldClient(channel)
                return client.queryUTxO(request)
            }
        }

        fun queryUTxOImp(utxo: UTxO): Int{
            val src_addr = utxo.addr
            val user_utxos = utxos[src_addr]
            println(user_utxos)
            val ret = Collections.frequency(Collections.unmodifiableList(user_utxos), utxo)

            println("num of ${utxo} found are ${ret}")

            return ret
        }

    }

}
