package com.kotlingrpc.demoGrpc.generated.main.grpckt.com.kotlingrpc.demoGrpc

import com.kotlingrpc.demoGrpc.GreeterGrpcKt
import messages.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit

class HelloWorldClient(private val channel: ManagedChannel) : Closeable {
    private val stub1: GetAllTxsGrpcKt.GetAllTxsCoroutineStub = GetAllTxsGrpcKt.GetAllTxsCoroutineStub(channel)
//    private val stub2: SubmitRequestGrpcKt.SubmitRequestCoroutineStub = SubmitRequestGrpcKt.SubmitRequestCoroutineStub(channel)

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

    val addr = args.singleOrNull() ?: "0x00000001"
    val n = 10.toLong()
//    client.get_history(addr, n)
//    client.add_request(10)
//    client.add_request(10)
//    client.add_request(10)
//    client.add_request(10)
//    client.add_request(11)
}