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

    private val public_stub = UserServicesGrpc.newBlockingStub(channel)
//    private val public_stub : UserServicesGrpcKt.UserServicesCoroutineStub = UserServicesGrpcKt.UserServicesCoroutineStub(channel)

      fun send_money(src_addr: String, dst_addr: String, coins: ULong) : SendMoneyResponse{
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
        try{
          val send_money_response = public_stub.withDeadlineAfter(60,TimeUnit.SECONDS).sendMoney(send_money_request)
          return send_money_response
        }
        catch(e:Exception){
            return sendMoneyResponse { txId="Server timed out. Check the ledger history and try again if needed" }
        }
    }
    fun getUtxos(addr: String,limit:Int):UTxOResponse{
        println("GET ${limit} UTXOS CLIENT")
        val get_utxo_request = UTxORequest.newBuilder().setAddr(addr).setLimit(
            limit.toLong()
        ).build()
        println("getting utxos for ${addr}")
        try{
            val get_utxo_response = public_stub.withDeadlineAfter(60,TimeUnit.SECONDS).getUTxOs(get_utxo_request)
            print("GOT UTXOS!")
            return get_utxo_response
        }
        catch(e:Exception){
            return uTxOResponse { utxos.addAll(listOf(uTxO { txId = "Server timed out. Check the ledger history and try again if needed" })) }
        }
    }
    fun getAddrHistory(addr:String,limit:Int):HistoryResponse{
        println("GET HISTORY CLIENT")
        val get_history_request = HistoryRequest.newBuilder().setAddr(addr).setLimit(
            limit.toLong()
        ).build()
        println("getting history for ${addr}")
        try{
            val get_history_response = public_stub.withDeadlineAfter(60,TimeUnit.SECONDS).getHistory(get_history_request)
            print("GOT history!")
            return get_history_response
        }
        catch(e:Exception){
            return historyResponse { txs.addAll(listOf(tx { txId = "Server timed out. Check the ledger history and try again if needed" })) }
        }
    }

    fun getEntireHistory():HistoryResponse{
        try{
            val entire_history_response = public_stub.withDeadlineAfter(60,TimeUnit.SECONDS).getEntireHistory(empty {  })
            return entire_history_response
        }
        catch(e:Exception){
            return historyResponse { txs.addAll(listOf(tx { txId = "Server timed out. Check the ledger history and try again if needed" })) }
        }
    }

    fun getShardHistory():HistoryResponse{
        val shard_history = public_stub.withDeadlineAfter(60,TimeUnit.SECONDS).getShardHistory(empty {  })
        return shard_history
    }


    fun sendInducedUTxO(utxo: UTxO): InternalResponse{
        println("HelloWorldClient: sendInducedUTxO: submitting induced utxo to server")
        return public_stub.withDeadlineAfter(60,TimeUnit.SECONDS).otherShardInducedUTxO(utxo)
    }

    fun submitTx(tx: Tx): SendMoneyResponse{
        println("HelloWorldClient: submitting transaction to server")

        try{
            val ret = public_stub.withDeadlineAfter(60,TimeUnit.SECONDS).submitTx(tx)
            return ret
        }
        catch(e:Exception){
            return sendMoneyResponse { txId="timeout" }
        }

    }

    fun submitAtomicTxList(tx_list: AtomicTxListRequest) : AtomicTxListResponse{
        println("HelloWorldClient: submitAtomicTxList: submitting to server")
        try{

            val ret = public_stub.withDeadlineAfter(60,TimeUnit.SECONDS).submitAtomicTxList(tx_list)
            return ret
        }
        catch(e:Exception){
            return atomicTxListResponse { txIdsList.addAll(listOf(sendMoneyResponse { txId = "Server timed out. Check the ledger history and try again if needed" })) }
        }
    }

    fun queryUTxO(utxo: UTxO) : QueryUTxOResponse{
        println("HelloWorldClient: queryUTxO: submitting submitAtomicTxList to server")
        val ret = public_stub.queryUTxO(utxo)
        return ret
    }
    fun setAtomicingFalse(utxo: UTxO):Empty  {
        println("Client - set atomicing to false")
        return public_stub.setAtomicingFalse(utxo)
    }
    fun setAtomicingTrue(utxo: UTxO):Empty  {
        println("Client - set atomicing to true")
        return public_stub.setAtomicingTrue(utxo)
    }

    fun addTx(tx: Tx) : InternalResponse{
        println("HelloWorldClient: updateFollowerLedger: submitting tx to server")
        val ret = public_stub.addTx(tx)
        return ret
    }

    fun addUTxOs(utxo_list: UTxOList) : InternalResponse{
        println("HelloWorldClient: updateFollowerUTxO: submitting utxo to server")
        val ret = public_stub.addUTxOs(utxo_list)
        return ret
    }

    override fun close() {
        channel.shutdown()
    }
}
