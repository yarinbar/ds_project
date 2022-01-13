package com.kotlingrpc.demoGrpc.generated.main.grpckt.com.kotlingrpc.demoGrpc

import com.google.protobuf.stringValue
import com.kotlingrpc.demoGrpc.GreeterGrpcKt
import messages.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.math.min

class HelloWorldClient(private val channel: ManagedChannel) : Closeable {
//    private val stub1: GetAllTxsGrpcKt.GetAllTxsCoroutineStub = GetAllTxsGrpcKt.GetAllTxsCoroutineStub(channel)
//    private val stub2: SubmitRequestGrpcKt.SubmitRequestCoroutineStub = SubmitRequestGrpcKt.SubmitRequestCoroutineStub(channel)
    val shard : Int = 0
    val num_shards : Int = 2

    private val public_stub : UserServicesGrpcKt.UserServicesCoroutineStub = UserServicesGrpcKt.UserServicesCoroutineStub(channel)
    private val internal_stub : InternalServicesGrpcKt.InternalServicesCoroutineStub = InternalServicesGrpcKt.InternalServicesCoroutineStub(channel)

    suspend fun get_history(addr: String, n: Int) : List<Tx>{

        val request = HistoryRequest.newBuilder()
            .setAddr(addr)
            .setLimit(n.toLong())
            .build()

        // retrieve all addresses
        if (addr == ""){
            println("Broadcast address - getting all history in my shard")
            val shard_history = public_stub.getHistory(request)
            println("Now getting all histories from other shards...")
            val other_shards_hist = public_stub.getHistory(request)
            // TODO - unify and sort results and use n to take top
            return shard_history.txsList
        }


        // specific address that is not in shard
        if ((addr.toBigInteger() % num_shards.toBigInteger()).toInt() != shard){
            println("That is not my shard, forwarding to correct shard")
            // TODO - forward to correct shard
            val empty_response = HistoryResponse.newBuilder()
                .build()

            return empty_response.txsList
        }

        println("That address is in my shard, fetching the relevant txs")
        val history = public_stub.getHistory(request)
        val history_list = history.txsList.toList()
//        val sorted_history: List<Tx> = history_list.sortedWith(compareByDescending { it.timestamp })


        // slicing relevant part
        var effictive_hist_len: Int

        if (n < 0){
            effictive_hist_len = history_list.size
        }
        else{
            effictive_hist_len = min(n, history_list.size)
        }
        val n_history_list = history_list.subList(0, effictive_hist_len)

        return n_history_list
    }

      suspend fun send_money(src_addr: String, dst_addr: String, coins: UInt) : String{

        if (coins.toInt() == 0){
            return "invalid coin amount, only takes positive ints"
        }

        val send_money_request = SendMoneyRequest.newBuilder()
            .setSrcAddr(src_addr)
            .setDstAddr(dst_addr)
            .setCoins(coins.toLong())
            .build()

        println("Attempting to send ${coins} coins from ${src_addr} to ${dst_addr}")
        val send_money_response = public_stub.sendMoney(request = send_money_request)
        println("Sent with tx_id ${send_money_response.txId}")
        return send_money_response.txId
    }

//    suspend fun greet(name: String) {
//        val request = HelloRequest.newBuilder().setName(name).build()
//        val response = stub.sayHello(request)
//        println("Received: ${response.message}")
//    }

//    suspend fun get_history(addr: String, n: Long){
//        val request = HistoryRequest.newBuilder().setAddr(addr).setN(n).build()
//        val response = stub1.getHistory(request)
//        println("Received: ${response.message}")
//
//    }
//
//    suspend fun add_request(id: Int){
//        val request = RequestObject.newBuilder().setId(id).build()
//        val response = stub2.addRequest(request)
//        println("${response}")
//    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}

/**
 * Greeter, uses first argument as name to greet if present;
 * greets "world" otherwise.
 */
suspend fun main(args: Array<String>) {
    val port = 50051

    val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()

    val client = HelloWorldClient(channel)

    val src_addr = args.singleOrNull() ?: "0000"
    val dst_addr = args.singleOrNull() ?: "0002"
    val n = 10
    client.send_money(src_addr, dst_addr, 15.toUInt())
    println(client.get_history(src_addr, n))
//    client.get_history(dst_addr, n)
//    client.add_request(10)
//    client.add_request(10)
//    client.add_request(10)
//    client.add_request(10)
//    client.add_request(11)
}