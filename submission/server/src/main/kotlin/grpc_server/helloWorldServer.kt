package grpc_server

import com.google.protobuf.Empty
import com.google.protobuf.boolValue
import com.google.protobuf.empty
import com.google.protobuf.util.Timestamps.fromMillis
import com.kotlingrpc.demoGrpc.generated.main.grpckt.com.kotlingrpc.demoGrpc.HelloWorldClient
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
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
import java.util.concurrent.TimeUnit


class HelloWorldServer(private val ip: String) {
    var utxos: HashMap<String, MutableList<UTxO>> = HashMap()
    var ledger: HashMap<String, MutableList<Tx>> = HashMap()
    var atomicing: Boolean = false
    var my_shard: Int = (System.getenv("SHARD") ?: "0").toInt()
    var num_shards: Int = System.getenv("NUM_SHARDS").toInt()
    val port: Int = System.getenv("PORT")?.toInt() ?: 50051
    var my_ip: String = ip

    val server: Server = ServerBuilder
        .forPort(port)
        .addService(UserServices())
        .build()

    fun get_zk(): ZooKeeper {
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
    val zkc = ZookeeperKtClient(zk)

    fun get_shard_nodes(shard_incharge: Int): List<String> {
        val path = "/SHARD_${shard_incharge}"
        val children = zk.getChildren(path, false)
            .sortedBy { ZKPaths.extractSequentialSuffix(it)!! }
        return children.map { it.toString().split('_')[0] }
    }

    fun get_shard_leader(shard_incharge: Int): String {
        println("trying to locate the correct shard's leader (shard ${shard_incharge})")
        return get_shard_nodes(shard_incharge).first()
    }

    fun tx_to_induced(tx: Tx): MutableList<UTxO> {
        // used to send to followers
        val induced_utxos: MutableList<UTxO> = mutableListOf()

        // save new induced utxos at dest utxos
        for ((i,tr) in tx.outputsList.withIndex()) {
            var utxo_string =tx.txId.plus(i.toString())
            var utxo_id = UUID.nameUUIDFromBytes(utxo_string.toByteArray()).toString()
            println("generating utxo with id ${utxo_id}")
            val induced_utxo = uTxO {
                txId = tx.txId
                utxoId = utxo_id
                addr = tr.addr
                coins = tr.coins
            }

            induced_utxos.add(induced_utxo)
        }
        return induced_utxos
    }

    suspend fun start() {
        server.start()
        println("registering with ZK")

        println("My ip is ${this.my_ip}")

        val existing_shards = zkc.getChildren("/").first
        if ("SHARD_${my_shard}" !in existing_shards) {
            println("Shard ${this.my_shard} doesn't exist, creating it")
            val (_, _) = zkc.create {
                path = "/SHARD_${my_shard}"
                flags = Persistent
            }
        }
        println("Joining shard ${this.my_shard}.")
        val (_, _) = zkc.create {
            path = "/SHARD_${my_shard}/${my_ip}_"
            flags = Ephemeral and Sequential
        }
        println("DONE registering with ZK")

        println("Server started, listening on $port")


        // genesis
        if (my_shard == 0) {
            println("Server for shard 0, creating genesis UTxO")
            val tx_id = "0x00000000001"
            val addr = "0000000000000000"
            val new_utxo = uTxO {
                this.txId = tx_id
                this.utxoId = "00000000-0000-0000-0000-000000000000"
                this.addr = addr
//                this.coins = Long.MAX_VALUE
                this.coins = 10000
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
            val int_addr = addr.toLong(radix = 16)
            return int_addr.mod(num_shards)
        }

        override suspend fun getEntireHistory(request: Empty): HistoryResponse {
//            for each shard leader get entire ledger, merge and order
            var ledger = mutableListOf<Tx>()
            for (i in 0 until System.getenv("NUM_SHARDS").toInt()) {
                println("GETTING HISTORY FOR SHARD ${i}")
                val shard_leader = get_shard_leader(i)
//                if (my_ip == shard_leader) {
//                    println("Correct node, handling request")
//                    return getHistoryImp(request)
//                }
                println("getting histroy from ${shard_leader}")
                val target_ip = shard_leader
                val channel = ManagedChannelBuilder.forAddress(target_ip, 50051).usePlaintext().build()
                val client = HelloWorldClient(channel)
                ledger.addAll(client.getShardHistory().txsList)
                channel.shutdown()

            }
            ledger = ledger.sortedWith(compareByDescending { it.timestamp.seconds}).toMutableList()
            return historyResponse {
                txs.addAll(ledger)
            }
        }

        override suspend fun getShardHistory(request: Empty): HistoryResponse {
            var shard_hist = ledger.values.toList().flatten()
            return historyResponse {
                txs.addAll(shard_hist)
            }
        }

        override suspend fun getHistory(request: HistoryRequest): HistoryResponse {

            val shard_leader = get_shard_leader(find_addr_shard(request.addr))
            if (my_ip == shard_leader) {
                println("Correct node, handling request")
                return getHistoryImp(request)
            }
            println("Wrong shard or not the leader! send to address ${shard_leader}")
            val target_ip = shard_leader
            val channel = ManagedChannelBuilder.forAddress(target_ip, 50051).usePlaintext().build()
            val client = HelloWorldClient(channel)
            val res = client.getAddrHistory(request.addr, request.limit.toInt())
            channel.shutdown()
            return res
        }

        fun getHistoryImp(request: HistoryRequest): HistoryResponse {

            val addr = request.addr
            var tx_hist = emptyList<Tx>()
            val addr_history = ledger[addr]

            if (addr_history != null) {
                tx_hist = addr_history.toList().sortedBy { it.timestamp.seconds }
                if (request.limit >= 0) {
                    println(tx_hist)
                    tx_hist = tx_hist.take(request.limit.toInt())
                    println(tx_hist)
                }
            }

            val grpc_tx_hist = historyResponse {
                txs.addAll(tx_hist)
            }

            return grpc_tx_hist
        }

        override suspend fun getUTxOs(request: UTxORequest): UTxOResponse {

            println("GET UTXO SERVER")
            val shard_leader = get_shard_leader(find_addr_shard(request.addr))
            if (my_ip == shard_leader) {
                println("Correct node, handling request")
                return getUTxOsImp(request)
            }
            println("Wrong shard or not the leader! send to address ${shard_leader}")
            val target_ip = shard_leader
            val channel = ManagedChannelBuilder.forAddress(target_ip, 50051).usePlaintext().build()
            val client = HelloWorldClient(channel)
            val res= client.getUtxos(request.addr, request.limit.toInt())
            channel.shutdown()
            return res
        }

        fun getUTxOsImp(request: UTxORequest): UTxOResponse {
            val addr = request.addr
            var utxos_hist = emptyList<UTxO>()
            val addr_history = utxos[addr]

            if (addr_history != null) {
                utxos_hist = addr_history.toList()
                if (request.limit >= 0) {
                    utxos_hist = utxos_hist.take(request.limit.toInt())
                }
            }

            val grpc_utxos_hist = uTxOResponse {
                utxos.addAll(utxos_hist)
            }

            return grpc_utxos_hist
        }

        override suspend fun sendMoney(request: SendMoneyRequest): SendMoneyResponse {
            val shard_leader = get_shard_leader(find_addr_shard(request.srcAddr))
            if (my_ip == shard_leader) {
                println("Correct node, handling request")
                return sendMoneyImp(request)
            }

            println("Wrong shard or not the leader! send to address ${shard_leader}")
            val target_ip = shard_leader
            val channel = ManagedChannelBuilder.forAddress(target_ip, 50051).usePlaintext().build()
            val client = HelloWorldClient(channel)
            var res = client.send_money(request.srcAddr, request.dstAddr, request.coins.toULong())
            channel.shutdown()
            return res
        }

        suspend fun sendMoneyImp(request: SendMoneyRequest): SendMoneyResponse {
            val src_addr = request.srcAddr
            val dst_addr = request.dstAddr
            val coins_ = request.coins.toULong()
            var grpc_send_money_response = sendMoneyResponse {
                txId = "-1"
            }
            //TODO (optional) find best utxos not just any utxos
            val available_utxos = utxos[src_addr]
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

            return submitTxImp(new_tx)
        }

        override suspend fun submitTx(request: Tx): SendMoneyResponse {
            val inputs = request.inputsList

            if (inputs.size == 0)
                return sendMoneyResponse {
                    txId = "tx needs to include at least 1 utxo"
                }

            val utxo_src_addr = request.inputsList[0].addr
            val target_shard = find_addr_shard(utxo_src_addr)
            val shard_leader = get_shard_leader(target_shard)

            // -------------- Checking if the UTxOs provided are present -------------
            // Option 1 - we are in the correct shard so we can check this address
            if (my_ip == shard_leader) {
                println("HelloWorldServer: submitTx: My shard and I am the leader - processing request")
                return submitTxImp(request)
            }
            // option 2 - we are not in the correct shard and need to forward the request
            else {
                println("HelloWorldServer: submitTx: Not my shard or not leader in my shard - not my problem")
                val channel = ManagedChannelBuilder.forAddress(shard_leader, 50051).usePlaintext().build()
                val client = HelloWorldClient(channel)
                var res= client.submitTx(request)
                channel.shutdown()
                return res
            }
        }

        suspend fun submitTxImp(request: Tx): SendMoneyResponse {
            println("LEADER - submitTxImp")
            while (atomicing)
                println("BUSY")

            println("submitTxImp: Beginning to process request")
            val validation_res = validateTx(request)

            // If tx is not valid for any reason, return the issue
            if (validation_res != request.txId) {
                println("submitTxImp: Done processing request, invalid, ending here.")
                return sendMoneyResponse {
                    txId = validation_res
                }
            }
            println("submitTxImp:Done processing request. Valid")

            val tx = request

            addTx(tx)
            println("HelloWorldServer: submitTxImp: Done successfully!")
            return sendMoneyResponse { txId = "Submitted successfully with ID ${tx.txId} "}
        }

        override suspend fun otherShardInducedUTxO(request: UTxO): InternalResponse {

            var res = addUTxOs(uTxOList { utxos.addAll(listOf(request)) })

            if (res.status != 0) {
                return res
            }

            val followers = get_shard_nodes(my_shard).minus(get_shard_leader(my_shard))
            println("HelloWorldServer: otherShardInducedUTxO: sending messages to ${followers}")

            for (follower in followers) {
                println("HelloWorldServer: otherShardInducedUTxO: sending to ${get_shard_nodes(my_shard)}")
                val channel = ManagedChannelBuilder.forAddress(follower, 50051).usePlaintext().build()
                val client = HelloWorldClient(channel)

                // adding induced
                res = client.addUTxOs(uTxOList {
                    utxos.addAll(listOf(request))
                })
                channel.shutdown()
                if (res.status != 0) {
                    return res
                }

            }
            return internalResponse { status = 0 }
        }

        suspend fun sendInducedUTxOS(utxo_list: List<UTxO>): InternalResponse {

            for (utxo in utxo_list) {
                println("HelloWorldServer: sendInducedUTxOS: sending ")
                val target_ip = get_shard_leader(find_addr_shard(utxo.addr))
                val channel = ManagedChannelBuilder.forAddress(target_ip, 50051).usePlaintext().build()
                val client = HelloWorldClient(channel)
                client.sendInducedUTxO(utxo)
                channel.shutdown()
            }

            return internalResponse { status = 0 }

        }

        override suspend fun addTx(request: Tx): InternalResponse {

            val src_addr = request.inputsList[0].addr
            val induced_utxos = tx_to_induced(request)
            induced_utxos.filter { find_addr_shard(it.addr) == my_shard }
            val this_shard_induced_utxos = induced_utxos.filter { find_addr_shard(it.addr) == my_shard }

            println("HelloWorldServer: addTx: server ${my_ip} in shard ${my_shard} adding ${request.txId} to ledger")

            // add to ledger in current shard
            if (ledger.containsKey(src_addr)) {
                if (ledger[src_addr]?.contains(request) == true){
                    println("HelloWorldServer: addTx: server ${my_ip} in shard ${my_shard} tried to add ${request.txId} but already exists! Ending here")
                    return internalResponse { status = 0 }
                }
                ledger[src_addr]?.add(request)
            } else {
                ledger[src_addr] = mutableListOf(request)
            }

            println("HelloWorldServer: addTx: server ${my_ip} in shard ${my_shard} added ${request.txId} to ledger successfully")
            for (utxo in request.inputsList) {
                val addr = utxo.addr
                if (!utxos.containsKey(addr) || utxos[addr]?.contains(utxo) == false) {
                    println("HelloWorldServer: rmUTxOs: server ${my_ip} in shard ${my_shard} cant find ${utxo.utxoId} from ${addr} ERROR")
                    continue
                }
                utxos[addr] = utxos[addr]!!.minus(utxo).toMutableList()
                println("HelloWorldServer: rmUTxOs: server ${my_ip} in shard ${my_shard} removed ${utxo.utxoId} from utxos successfully")
            }

            for (utxo in induced_utxos) {
                val addr = utxo.addr
                println("HelloWorldServer: addUTxOs: server ${my_ip} in shard ${my_shard} adding ${utxo.utxoId} to utxos")
                if (utxos.containsKey(addr)) {
                    if (utxos[addr]?.contains(utxo) == true){
                        println("HelloWorldServer: addUTxOs: server ${my_ip} in shard ${my_shard} tried to add ${utxo.utxoId} but already exists!")
                        continue
                    }
                    utxos[addr]?.add(utxo)
                } else {
                    utxos[addr] = mutableListOf(utxo)
                }
                println("HelloWorldServer: addUTxOs: server ${my_ip} in shard ${my_shard} added ${utxo.utxoId} to utxos successfully")
            }

            println("HelloWorldServer: addTx: im gossiping! ${my_ip}")
            val followers = get_shard_nodes(my_shard).minus(get_shard_leader(my_shard))

            for (follower in followers) {

                if (follower == my_ip)
                    continue

                println("HelloWorldServer: addTx: sending gossip to ${follower}")

                val channel = ManagedChannelBuilder.forAddress(follower, 50051).usePlaintext().build()
                val client = HelloWorldClient(channel)
                client.addTx(request)
                channel.shutdown()
            }

            println("sending induced utxos")
            val other_shards_induced_utxos = induced_utxos.filter { it !in this_shard_induced_utxos }
            sendInducedUTxOS(other_shards_induced_utxos)
            return internalResponse { status = 0 }
        }

        override suspend fun addUTxOs(request: UTxOList): InternalResponse {

            val utxo_list = request.utxosList
            var need_to_gossip = true

            for (utxo in utxo_list) {
                val addr = utxo.addr
                println("HelloWorldServer: addUTxOs: server ${my_ip} in shard ${my_shard} adding ${utxo.utxoId} to utxos")
                if (utxos.containsKey(addr)) {

                    if (utxos[addr]?.contains(utxo) == true){
                        println("HelloWorldServer: addUTxOs: server ${my_ip} in shard ${my_shard} tried to add ${utxo.utxoId} but already exists!")
                        need_to_gossip = false
                        continue
                    }

                    utxos[addr]?.add(utxo)
                } else {
                    utxos[addr] = mutableListOf(utxo)
                }
                println("HelloWorldServer: addUTxOs: server ${my_ip} in shard ${my_shard} added ${utxo.utxoId} to utxos successfully")
            }

            if (need_to_gossip){
                println("HelloWorldServer: addUTxOs: im gossiping! ${my_ip}")
                val followers = get_shard_nodes(my_shard).minus(get_shard_leader(my_shard))

                for (follower in followers) {

                    println("HelloWorldServer: addUTxOs: sending gossip to ${follower}")

                    if (follower == my_ip)
                        continue

                    val channel = ManagedChannelBuilder.forAddress(follower, 50051).usePlaintext().build()
                    val client = HelloWorldClient(channel)

                    // removing used
                    client.addUTxOs(request)
                    channel.shutdown()

                }
            }

            return internalResponse { status = 0 }
        }

        fun validateTx(request: Tx): String {

            val inputs = request.inputsList
            val outputs = request.outputsList
            val utxo_src_addr = request.inputsList[0].addr
            val user_utxos = utxos[utxo_src_addr]

            println("HelloWorldServer: submitTxImp: UTxOs found for address ${utxo_src_addr}:\n${user_utxos}")

            val output_coins = outputs.map { it.coins }.sum()
            val input_coins = inputs.map { it.coins }.sum()

            if (output_coins != input_coins) {
                return "utxo sum must match outputs sum, utxo sum: ${input_coins} output sum: ${output_coins}"
            }

            return request.txId
        }

        override suspend fun submitAtomicTxList(request: AtomicTxListRequest): AtomicTxListResponse {
            val shard_leader = get_shard_leader(find_addr_shard(request.txListList[0].inputsList[0].addr))

            if (my_ip == shard_leader) {
                println("HelloWorldServer: submitAtomicTxList: My shard and I am the leader - processing request")
                return submitAtomicTxListImp(request)
            }
            // option 2 - we are not in the correct shard and need to forward the request
            else {
                println("HelloWorldServer: submitAtomicTxList: Not my shard or not leader in my shard - not my problem")

                val channel = ManagedChannelBuilder.forAddress(shard_leader, 50051).usePlaintext().build()
                val client = HelloWorldClient(channel)
                var res= client.submitAtomicTxList(request)
                channel.shutdown()
                return res
            }
        }

        suspend fun submitAtomicTxListImp(request: AtomicTxListRequest): AtomicTxListResponse{
            if (!validateAtomicTxList(request))
                return atomicTxListResponse {
                    txIdsList.addAll(listOf(sendMoneyResponse { txId = "not valid atomic tx list" }))
                }
            val response_list: MutableList<SendMoneyResponse> = mutableListOf()

            for (tx in request.txListList) {
                setAtomicingFalse(tx.inputsList[0])
                response_list.add(submitTx(tx))
                setAtomicingTrue(tx.inputsList[0])
            }

            for (tx in request.txListList)
                setAtomicingFalse(tx.inputsList[0])

            return atomicTxListResponse { txIdsList.addAll(response_list) }
        }


        suspend fun validateAtomicTxList(request: AtomicTxListRequest): Boolean {
            val all_utxos_required: List<UTxO> = request.txListList.map { it.inputsList }.flatten()

            // Checking that all utxos used are present
            for (utxo in all_utxos_required.distinct()) {
                if (Collections.frequency(all_utxos_required, utxo) > queryUTxO(utxo).occurrences) {
                    for (utxo in all_utxos_required.distinct()) {
                        setAtomicingFalse(utxo)
                    }
                    return false
                }
            }

            for (tx in request.txListList){
                val inputs = tx.inputsList
                val outputs = tx.outputsList

                val input_coins = inputs.map { it.coins }.sum()
                val output_coins = outputs.map { it.coins }.sum()

                if (output_coins !=input_coins) {
                    return false
                }
            }
            return true
        }

        override suspend fun setAtomicingFalse(request: UTxO): Empty {
            val src_addr = request.addr
            val target_shard = find_addr_shard(src_addr)
            val shard_leader = get_shard_leader(target_shard)

            // -------------- Checking if the UTxOs provided are present -------------
            // Option 1 - we are in the correct shard so we can check this address
            if (my_ip == shard_leader) {
                println("HelloWorldServer: queryUTxO: My shard and I am the leader - setting atomicing back to false")
                atomicing = false
                return empty { }
            }
            // option 2 - we are not in the correct shard and need to forward the request
            else {
                println("HelloWorldServer: queryUTxO: Not my shard or not leader in my shard - not my problem")
                val channel = ManagedChannelBuilder.forAddress(shard_leader, 50051).usePlaintext().build()
                val client = HelloWorldClient(channel)
                var res =client.setAtomicingFalse(request)
                channel.shutdown()
                return res
            }
        }

        override suspend fun setAtomicingTrue(request: UTxO): Empty {
            val src_addr = request.addr
            val target_shard = find_addr_shard(src_addr)
            val shard_leader = get_shard_leader(target_shard)

            // -------------- Checking if the UTxOs provided are present -------------
            // Option 1 - we are in the correct shard so we can check this address
            if (my_ip == shard_leader) {
                println("HelloWorldServer: queryUTxO: My shard and I am the leader - setting atomicing back to false")
                atomicing = true
                return empty { }
            }
            // option 2 - we are not in the correct shard and need to forward the request
            else {
                println("HelloWorldServer: queryUTxO: Not my shard or not leader in my shard - not my problem")
                val channel = ManagedChannelBuilder.forAddress(shard_leader, 50051).usePlaintext().build()
                val client = HelloWorldClient(channel)
                val res = client.setAtomicingTrue(request)
                channel.shutdown()
                return res
            }
        }

        override suspend fun queryUTxO(request: UTxO): QueryUTxOResponse {

            val src_addr = request.addr
            val target_shard = find_addr_shard(src_addr)
            val shard_leader = get_shard_leader(target_shard)

            // -------------- Checking if the UTxOs provided are present -------------
            // Option 1 - we are in the correct shard so we can check this address
            if (my_ip == shard_leader) {
                println("HelloWorldServer: queryUTxO: My shard and I am the leader - processing request")
                return queryUTxOResponse { occurrences = queryUTxOImp(request) }
            }
            // option 2 - we are not in the correct shard and need to forward the request
            else {
                println("HelloWorldServer: queryUTxO: Not my shard or not leader in my shard - not my problem")
                val channel = ManagedChannelBuilder.forAddress(shard_leader, 50051).usePlaintext().build()
                val client = HelloWorldClient(channel)
                var res = client.queryUTxO(request)
                channel.shutdown()
                return res
            }
        }

        fun queryUTxOImp(utxo: UTxO): Int {
            atomicing = true
            val src_addr = utxo.addr
            val user_utxos = utxos[src_addr]
            println(user_utxos)
            val ret = Collections.frequency(Collections.unmodifiableList(user_utxos), utxo)

            println("num of ${utxo} found are ${ret}")

            return ret
        }

    }

}
