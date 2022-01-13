package com.kotlingrpc.demoGrpc.generated.main.grpckt.com.kotlingrpc.demoGrpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import messages.*
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.reflect.jvm.internal.impl.builtins.StandardNames.FqNames.target


class HelloWorldClient(private val channel: ManagedChannel) : Closeable {

    private val public_stub : UserServicesGrpcKt.UserServicesCoroutineStub = UserServicesGrpcKt.UserServicesCoroutineStub(channel)
    private val internal_stub : InternalServicesGrpcKt.InternalServicesCoroutineStub = InternalServicesGrpcKt.InternalServicesCoroutineStub(channel)
    var num_shards : Int = System.getenv("NUM_SHARDS").toInt()

    fun find_addr_shard(addr : String) : Int{
        val int_addr = addr.toBigInteger()
        return int_addr.mod(num_shards.toBigInteger()).toInt()
    }

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
//        if ((addr.toBigInteger() % num_shards.toBigInteger()).toInt() != shard){
//            println("That is not my shard, forwarding to correct shard")
//            // TODO - forward to correct shard
//            val empty_response = HistoryResponse.newBuilder()
//                .build()
//
//            return empty_response.txsList
//        }

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

      suspend fun send_money(src_addr: String, dst_addr: String, coins: UInt) : SendMoneyResponse{
        println("SEND MONEY CLIENT")
        if (coins.toInt() == 0){
            var grpc_send_money_response = sendMoneyResponse {
                txId = "-1"
            }
            return grpc_send_money_response
        }
        val send_money_request = SendMoneyRequest.newBuilder()
            .setSrcAddr(src_addr)
            .setDstAddr(dst_addr)
            .setCoins(coins.toLong())
            .build()

        println("Attempting to send ${coins} coins from ${src_addr} to ${dst_addr}")
        val send_money_response = public_stub.sendMoney(request = send_money_request)
        println("Sent with tx_id ${send_money_response.txId}")
        return send_money_response
    }


    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
