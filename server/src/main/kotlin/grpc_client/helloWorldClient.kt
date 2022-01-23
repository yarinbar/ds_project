package com.kotlingrpc.demoGrpc.generated.main.grpckt.com.kotlingrpc.demoGrpc

import com.google.protobuf.Empty
import com.google.protobuf.empty
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import messages.*
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.reflect.jvm.internal.impl.builtins.StandardNames.FqNames.target


class HelloWorldClient(private val channel: ManagedChannel) : Closeable {

    private val public_stub : UserServicesGrpcKt.UserServicesCoroutineStub = UserServicesGrpcKt.UserServicesCoroutineStub(channel)
    var num_shards : Int = System.getenv("NUM_SHARDS").toInt()



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

      suspend fun send_money(src_addr: String, dst_addr: String, coins: ULong) : SendMoneyResponse{
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
    suspend fun getUtxos(addr: String,limit:Int):UTxOResponse{
        println("GET ${limit} UTXOS CLIENT")
        val get_utxo_request = UTxORequest.newBuilder().setAddr(addr).setLimit(
            limit.toLong()
        ).build()
        println("getting utxos for ${addr}")
        val get_utxo_response = public_stub.getUTxOs(request = get_utxo_request)
        print("GOT UTXOS!")
        return get_utxo_response
    }
    suspend fun getAddrHistory(addr:String,limit:Int):HistoryResponse{
        println("GET HISTORY CLIENT")
        val get_history_request = HistoryRequest.newBuilder().setAddr(addr).setLimit(
            limit.toLong()
        ).build()
        println("getting history for ${addr}")
        val get_history_response = public_stub.getHistory(request = get_history_request)
        print("GOT history!")
        return get_history_response
    }

    suspend fun getEntireHistory():HistoryResponse{
        val entire_history_response = public_stub.getEntireHistory(empty {  })
        return entire_history_response
    }

    suspend fun getShardHistory():HistoryResponse{
        val shard_history = public_stub.getShardHistory(empty {  })
        return shard_history
    }


    suspend fun sendInducedUTxO(utxo: UTxO): InternalResponse{
        println("HelloWorldClient: sendInducedUTxO: submitting induced utxo to server")
        return public_stub.otherShardInducedUTxO(utxo)
    }

    suspend fun submitTx(tx: Tx): SendMoneyResponse{
        println("HelloWorldClient: submitting transaction to server")
        val ret = public_stub.submitTx(tx)
        return ret
    }

    suspend fun submitAtomicTxList(tx_list: AtomicTxListRequest) : AtomicTxListResponse{
        println("HelloWorldClient: submitAtomicTxList: submitting to server")
        val ret = public_stub.submitAtomicTxList(tx_list)
        return ret
    }

    suspend fun queryUTxO(utxo: UTxO) : QueryUTxOResponse{
        println("HelloWorldClient: queryUTxO: submitting submitAtomicTxList to server")
        val ret = public_stub.queryUTxO(utxo)
        return ret
    }
    suspend fun setAtomicingFalse(utxo: UTxO):Empty  {
        println("Client - set atomicing to false")
        return public_stub.setAtomicingFalse(utxo)
    }
    suspend fun setAtomicingTrue(utxo: UTxO):Empty  {
        println("Client - set atomicing to true")
        return public_stub.setAtomicingTrue(utxo)
    }

    suspend fun addTx(tx: Tx) : InternalResponse{
        println("HelloWorldClient: updateFollowerLedger: submitting tx to server")
        val ret = public_stub.addTx(tx)
        return ret
    }

    suspend fun addUTxOs(utxo_list: UTxOList) : InternalResponse{
        println("HelloWorldClient: updateFollowerUTxO: submitting utxo to server")
        val ret = public_stub.addUTxOs(utxo_list)
        return ret
    }

    suspend fun rmUTxOs(utxo_list: UTxOList) : InternalResponse{
        println("HelloWorldClient: updateFollowerUTxO: submitting utxo to server")
        val ret = public_stub.rmUTxOs(utxo_list)
        return ret
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
