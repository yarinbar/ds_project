package com.kotlingrpc.demoGrpc.generated.main.grpckt.com.kotlingrpc.demoGrpc


import io.grpc.Server
import io.grpc.ServerBuilder
import messages.*
import messages.GrpcRequests.*
import java.sql.Timestamp
import java.time.OffsetDateTime


class HelloWorldServer(private val port: Int) {
    var utxos: HashMap<String, MutableList<UTxO>> = HashMap<String,MutableList<UTxO>> ()
    var ledger: HashMap<String, MutableList<Tx>> = HashMap<String,MutableList<Tx>> ()
    val shard : Int = 0
    val num_shards : Int = 2

    val server: Server = ServerBuilder
        .forPort(port)
        .addService(UserServices())
        .addService(InternalServices())
        .build()

    fun start() {
        server.start()
        println("Server started, listening on $port")

        println("Creating genesis UTxO")

//        val tx_id = 0.toString()
//        val addr = 0.toString()
//
//        val new_utxo = UTxO(tx_id, addr)
//        val new_utxo_w_val = UTxOWithValue(new_utxo, 100.toULong())
//
//        utxos.put(addr, mutableListOf(new_utxo_w_val))
//        println("Created genesis UTxO")


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

//    fun addToTable(id: Int){
//        val timestamp = Timestamp(System.currentTimeMillis()).toString()
//        if (req_hash.containsKey(id)){
//            req_hash.get(id)?.add(timestamp)
//        }
//        else {
//            req_hash.put(id, mutableListOf(timestamp))
//        }
//    }


    inner class UserServices : UserServicesGrpcKt.UserServicesCoroutineImplBase(){
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
                // TODO - figure out why it doesnt work
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

            val src_addr = request.srcAddr
            val dst_addr = request.dstAddr
            val coins = request.coins.toULong()

            var grpc_send_money_response = sendMoneyResponse {
                txId = "-1"
            }

            // If Leader in correct shard
            val available_utxos = utxos.get(src_addr)

            println(available_utxos)

            var sumUTxOs = 0.toULong()
            var utxo_list = mutableListOf<UTxO>()

            if (available_utxos != null) {
                for (utxo in available_utxos) {
                    sumUTxOs+= utxo.coins.toULong()
                    utxo_list.add(utxo)
                    if (sumUTxOs >= coins)
                        break
                }
            }

            if (sumUTxOs < coins) {
                return grpc_send_money_response
            }

            // TODO - call id_gen func
            val tx_id = "noam_and_yarin_want_to_be_over_this!"
            val transfers = mutableListOf<Tr>()

            transfers.add(tr {
                addr = dst_addr
//                coins = coins
            })

            if (sumUTxOs != coins) {
                transfers.add(
                    tr {
                        addr = src_addr
//                        coins = sumUTxOs - coins
                    }
                )
            }

//            val javaTimeInstant = System.currentTimeMillis().toInstant()
//            val ts = com.google.protobuf.Timestamp.newBuilder()
//                .setSeconds(javaTimeInstant.epochSecond)
//                .setNanos(javaTimeInstant.nano)
//                .build()

            // add to ledger in current shard
            val new_tx = tx {
                txId = tx_id
                inputs.addAll(utxo_list)
                outputs.addAll(transfers)
//                timestamp = Timestamp(System.currentTimeMillis())
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
//                    coins = tr.coins
                }

                // TODO - fix this
                if (shard == shard) {
                    // add to utxo hashmap, check if addr is already mapped first
                    if (utxos.containsKey(src_addr)) {
                        utxos.get(src_addr)?.add(induced_utxo)
                    } else {
                        utxos.put(tr.addr, mutableListOf(induced_utxo))
                    }
                } else {
                    // TODO - transfer to correct shard
                }

            }
            //remove used utxos
            print(available_utxos)
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

            grpc_send_money_response = sendMoneyResponse {
                txId = "success mother fucker"
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

fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 50051
    val server = HelloWorldServer(port)
    server.start()
    server.blockUntilShutdown()
}