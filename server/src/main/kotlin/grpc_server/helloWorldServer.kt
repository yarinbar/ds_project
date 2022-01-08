package com.kotlingrpc.demoGrpc.generated.main.grpckt.com.kotlingrpc.demoGrpc


import com.kotlingrpc.demoGrpc.GreeterGrpcKt
import com.kotlingrpc.demoGrpc.HelloReply
import com.kotlingrpc.demoGrpc.HelloRequest
import io.grpc.Server
import io.grpc.ServerBuilder
import messages.*
import server.TxWithTime
import server.UTxO
import server.UTxOWithValue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.sql.Timestamp
import java.util.HashMap



class HelloWorldServer(private val port: Int) {
    var utxos: HashMap<String, MutableList<UTxOWithValue>> = HashMap<String,MutableList<UTxOWithValue>> ()
    var ledger: HashMap<String, MutableList<TxWithTime>> = HashMap<String,MutableList<TxWithTime>> ()
    val shard : Int = 0
    val num_shards : Int = 2

    val server: Server = ServerBuilder
        .forPort(port)
        .addService(HelloWorldService())

        .build()

    fun start() {
        server.start()
        println("Server started, listening on $port")

        println("Creating genesis UTxO")

        val tx_id = 0.toString()
        val addr = 0.toString()

        val new_utxo = UTxO(tx_id, addr)
        val new_utxo_w_val = UTxOWithValue(new_utxo, 100.toULong())

        utxos.put(addr, mutableListOf(new_utxo_w_val))
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

//    fun addToTable(id: Int){
//        val timestamp = Timestamp(System.currentTimeMillis()).toString()
//        if (req_hash.containsKey(id)){
//            req_hash.get(id)?.add(timestamp)
//        }
//        else {
//            req_hash.put(id, mutableListOf(timestamp))
//        }
//    }

    private class HelloWorldService : GreeterGrpcKt.GreeterCoroutineImplBase() {
        override suspend fun sayHello(request: HelloRequest) = HelloReply
            .newBuilder()
            .setMessage("Hello ${request.name}")
            .build()
    }

    inner class GetUTxOs : GetUTxOsGrpcKt.GetUTxOsCoroutineImplBase(){
        override suspend fun getUTxOs(request: GrpcRequests.UTxORequest): GrpcRequests.UTxOResponse {
//            if (request.id % num_shards != shard){
//                println("Got request from ${request.id} but that is NOT my shard (I am shard ${shard}) sending to shard ${request.id % num_shards}")
//                val response = ResponseObject
//                    .newBuilder()
//                    .build()
//                return response
//            }
//            addToTable(request.id)
//            println("Added ${request.id} successfully")
//            val req_by_id = req_hash.get(request.id)
            val id = 0.toString();

            val addr_utxos = utxos.get(id)
            val utxos_no_val = mutableListOf<UTxO>()

            if (addr_utxos != null) {
                addr_utxos.forEach({i -> utxos_no_val.add(i.uTxO)})
            }

            val response = GrpcRequests.UTxOResponse
                .newBuilder()
                .addAllUtxos(utxos_no_val)
                .build()

            return response

        }
    }

//    private class GetAllTransactions : GetAllTxsGrpcKt.GetAllTxsCoroutineImplBase(){
//        override suspend fun getHistory(request: HistoryRequest) = HistoryResponse
//            .newBuilder()
//            .setMessage("Hello This ${request.addr} ${request.n}")
//            .build()
//    }

//    inner class ProcessRequest : SubmitRequestGrpcKt.SubmitRequestCoroutineImplBase(){
//        override suspend fun addRequest(request: RequestObject): ResponseObject {
//            if (request.id % num_shards != shard){
//                println("Got request from ${request.id} but that is NOT my shard (I am shard ${shard}) sending to shard ${request.id % num_shards}")
//                val response = ResponseObject
//                    .newBuilder()
//                    .build()
//                return response
//            }
////            addToTable(request.id)
////            println("Added ${request.id} successfully")
////            val req_by_id = req_hash.get(request.id)
//            val response = ResponseObject
//                .newBuilder()
//                .build()
//
//            return response
//        }
//    }

}
fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 50051
    val server = HelloWorldServer(port)
    server.start()
    server.blockUntilShutdown()
}